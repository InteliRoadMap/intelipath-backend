package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.CourseRequest;
import com.inteliroadmap.backend.domain.dto.response.course.CourseLessonResponse;
import com.inteliroadmap.backend.domain.dto.response.course.CourseResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Course;
import com.inteliroadmap.backend.domain.entity.CourseEnrollment;
import com.inteliroadmap.backend.domain.entity.CourseLesson;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.CourseLevel;
import com.inteliroadmap.backend.domain.enums.CourseStatus;
import com.inteliroadmap.backend.domain.enums.EnrollmentStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.ForbiddenException;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.CourseEnrollmentRepository;
import com.inteliroadmap.backend.repositories.CourseLessonRepository;
import com.inteliroadmap.backend.repositories.CourseRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseLessonRepository lessonRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;

    // ---------------------------------------------------------------- Mentor

    @Override
    @Transactional
    public CourseResponse createCourse(CourseRequest request) {
        UUID mentorId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        Course course = Course.builder()
                .mentorId(mentorId)
                .careerId(request.getCareerId())
                .nodeId(request.getNodeId())
                .title(request.getTitle())
                .description(request.getDescription())
                .level(request.getLevel() != null ? request.getLevel() : CourseLevel.BEGINNER)
                .status(CourseStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        course = courseRepository.save(course);
        saveLessons(course.getCourseId(), request);
        log.info("CourseServiceImpl: mentor {} created course {}", mentorId, course.getCourseId());
        return toResponse(course, true, null);
    }

    @Override
    @Transactional
    public CourseResponse updateCourse(UUID courseId, CourseRequest request) {
        Course course = ownedCourse(courseId);
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setCareerId(request.getCareerId());
        course.setNodeId(request.getNodeId());
        if (request.getLevel() != null) {
            course.setLevel(request.getLevel());
        }
        course.setUpdatedAt(LocalDateTime.now());
        course = courseRepository.save(course);

        // Replace the lesson set with what the mentor submitted.
        lessonRepository.deleteByCourseId(courseId);
        saveLessons(courseId, request);
        return toResponse(course, true, null);
    }

    @Override
    @Transactional
    public void deleteCourse(UUID courseId) {
        Course course = ownedCourse(courseId);
        // course_lessons and course_enrollments are removed by ON DELETE CASCADE.
        courseRepository.delete(course);
    }

    @Override
    @Transactional
    public CourseResponse setPublished(UUID courseId, boolean published) {
        Course course = ownedCourse(courseId);
        course.setStatus(published ? CourseStatus.PUBLISHED : CourseStatus.DRAFT);
        course.setUpdatedAt(LocalDateTime.now());
        return toResponse(courseRepository.save(course), false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getMentorCourses() {
        return courseRepository.findByMentorIdOrderByUpdatedAtDesc(currentUserId())
                .stream().map(c -> toResponse(c, false, null)).toList();
    }

    // --------------------------------------------------------------- Student

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> browseCourses(UUID careerId, UUID nodeId) {
        UUID studentId = currentUserId();
        List<Course> courses;
        if (nodeId != null) {
            courses = courseRepository.findByStatusAndNodeIdOrderByCreatedAtDesc(CourseStatus.PUBLISHED, nodeId);
        } else if (careerId != null) {
            courses = courseRepository.findByStatusAndCareerIdOrderByCreatedAtDesc(CourseStatus.PUBLISHED, careerId);
        } else {
            courses = courseRepository.findByStatusOrderByCreatedAtDesc(CourseStatus.PUBLISHED);
        }
        return courses.stream().map(c -> toResponse(c, false, studentId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse getCourseDetail(UUID courseId) {
        UUID userId = currentUserId();
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        // Drafts are only visible to their author.
        if (course.getStatus() == CourseStatus.DRAFT && !course.getMentorId().equals(userId)) {
            throw new ResourceNotFoundException("Course not found");
        }
        return toResponse(course, true, userId);
    }

    @Override
    @Transactional
    public CourseResponse enroll(UUID courseId) {
        UUID studentId = currentUserId();
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Course not found");
        }
        if (!enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            enrollmentRepository.save(CourseEnrollment.builder()
                    .courseId(courseId)
                    .studentId(studentId)
                    .status(EnrollmentStatus.ENROLLED)
                    .progress(0)
                    .enrolledAt(LocalDateTime.now())
                    .build());
            log.info("CourseServiceImpl: student {} enrolled in course {}", studentId, courseId);
        }
        return toResponse(course, true, studentId);
    }

    @Override
    @Transactional
    public void unenroll(UUID courseId) {
        UUID studentId = currentUserId();
        enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .ifPresent(enrollmentRepository::delete);
    }

    @Override
    @Transactional
    public CourseResponse updateProgress(UUID courseId, int progress) {
        UUID studentId = currentUserId();
        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new ForbiddenException("You are not enrolled in this course"));
        int clamped = Math.max(0, Math.min(100, progress));
        enrollment.setProgress(clamped);
        enrollment.setStatus(clamped >= 100 ? EnrollmentStatus.COMPLETED : EnrollmentStatus.ENROLLED);
        enrollmentRepository.save(enrollment);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        return toResponse(course, false, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getMyEnrollments() {
        UUID studentId = currentUserId();
        return enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(studentId).stream()
                .map(e -> courseRepository.findById(e.getCourseId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(c -> toResponse(c, false, studentId))
                .toList();
    }

    // ---------------------------------------------------------------- Helpers

    private void saveLessons(UUID courseId, CourseRequest request) {
        if (request.getLessons() == null) {
            return;
        }
        int index = 0;
        for (CourseRequest.LessonRequest l : request.getLessons()) {
            lessonRepository.save(CourseLesson.builder()
                    .courseId(courseId)
                    .title(l.getTitle())
                    .content(l.getContent())
                    .resourceUrl(l.getResourceUrl())
                    .orderIndex(index++)
                    .build());
        }
    }

    /** Load a course and assert the current user authored it. */
    private Course ownedCourse(UUID courseId) {
        UUID mentorId = currentUserId();
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!course.getMentorId().equals(mentorId)) {
            throw new ResourceNotFoundException("Course not found");
        }
        return course;
    }

    private CourseResponse toResponse(Course c, boolean includeLessons, UUID studentId) {
        String mentorName = userRepository.findById(c.getMentorId()).map(User::getFullName).orElse(null);
        String careerName = c.getCareerId() == null ? null
                : careerRoleRepository.findById(c.getCareerId()).map(CareerRole::getCareerName).orElse(null);
        String nodeName = c.getNodeId() == null ? null
                : skillNodeRepository.findById(c.getNodeId()).map(n -> n.getNodeName()).orElse(null);

        CourseResponse.CourseResponseBuilder b = CourseResponse.builder()
                .courseId(c.getCourseId())
                .title(c.getTitle())
                .description(c.getDescription())
                .level(c.getLevel())
                .status(c.getStatus())
                .mentorId(c.getMentorId())
                .mentorName(mentorName)
                .careerId(c.getCareerId())
                .careerName(careerName)
                .nodeId(c.getNodeId())
                .nodeName(nodeName)
                .lessonCount(lessonRepository.countByCourseId(c.getCourseId()))
                .enrolledCount(enrollmentRepository.countByCourseId(c.getCourseId()))
                .createdAt(c.getCreatedAt())
                .enrolled(false);

        if (includeLessons) {
            List<CourseLessonResponse> lessons = lessonRepository.findByCourseIdOrderByOrderIndexAsc(c.getCourseId())
                    .stream().map(this::toLessonResponse).toList();
            b.lessons(lessons);
        }
        if (studentId != null) {
            enrollmentRepository.findByCourseIdAndStudentId(c.getCourseId(), studentId)
                    .ifPresent(e -> b.enrolled(true).progress(e.getProgress()));
        }
        return b.build();
    }

    private CourseLessonResponse toLessonResponse(CourseLesson l) {
        return CourseLessonResponse.builder()
                .lessonId(l.getLessonId())
                .title(l.getTitle())
                .content(l.getContent())
                .resourceUrl(l.getResourceUrl())
                .orderIndex(l.getOrderIndex())
                .build();
    }

    private UUID currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        return user.getUserId();
    }
}
