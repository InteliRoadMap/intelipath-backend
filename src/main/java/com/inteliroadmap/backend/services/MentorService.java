package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDashboardMetrics;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.ReviewStatus;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorService {

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final PortfolioReviewRequestRepository reviewRequestRepository;
    private final StudentRepository studentRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final SkillNodeRepository skillNodeRepository;

    private User getAuthenticatedMentor() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null || user.getRole() != UserRole.MENTOR) {
            throw new ResourceNotFoundException("Mentor not found from token or invalid role");
        }
        return user;
    }

    @Transactional
    public MentorResponse getDashboardMetrics() {
        log.info("Get mentor dashboard metrics request received");
        User mentor = getAuthenticatedMentor();

        Double avgRating = feedbackRepository.getAverageRatingBySenderId(mentor.getUserId());
        double rating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;

        Double avgTimeSec = reviewRequestRepository.getAverageResponseTimeInSecondsByMentorId(mentor.getUserId());
        String responseTime = formatResponseTime(avgTimeSec);

        long pendingReviews = reviewRequestRepository.countByMentor_UserIdAndStatus(mentor.getUserId(), ReviewStatus.PENDING);
        
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7); // Simple last 7 days for now
        long feedbacksThisWeek = feedbackRepository.countFeedbacksBySenderIdSince(mentor.getUserId(), startOfWeek);

        // Mentees count (students who have sent at least one review request to this mentor)
        long menteesCount = reviewRequestRepository.countDistinctStudentsByMentorId(mentor.getUserId());

        MentorDashboardMetrics metrics = MentorDashboardMetrics.builder()
                .rating(rating)
                .responseTime(responseTime)
                .mentees(menteesCount)
                .pendingReviews(pendingReviews)
                .feedbacks(feedbacksThisWeek)
                .build();

        return MentorResponse.builder().metrics(metrics).build();
    }

    private String formatResponseTime(Double seconds) {
        if (seconds == null || seconds == 0) return "0h 0m";
        int totalMinutes = (int) (seconds / 60);
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        return hours + "h " + mins + "m";
    }

    @Transactional
    public MentorResponse getPendingReviews(Pageable pageable) {
        User mentor = getAuthenticatedMentor();
        Page<PortfolioReviewRequest> requests = reviewRequestRepository.findByMentor_UserIdAndStatus(mentor.getUserId(), ReviewStatus.PENDING, pageable);
        return MentorResponse.builder().pendingReviews(requests).build();
    }

    @Transactional
    public MentorResponse getStudentInfos(Pageable pageable) {
        User mentor = getAuthenticatedMentor();
        Page<User> studentUsersPage = reviewRequestRepository.findDistinctStudentsByMentorId(mentor.getUserId(), pageable);
        
        List<Map<String, Object>> stInfos = new ArrayList<>();

        // --- OPTIMIZED N+1 QUERY ---
        List<UUID> userIds = studentUsersPage.getContent().stream().map(User::getUserId).collect(Collectors.toList());
        List<Student> students = studentRepository.findAllById(userIds); // Note: student id is same as user id
        Map<UUID, Student> studentMap = students.stream().collect(Collectors.toMap(Student::getUserId, s -> s));

        List<SkillNode> allNodes = skillNodeRepository.findAll();
        Map<UUID, List<SkillNode>> nodesByCareerId = allNodes.stream()
                .filter(node -> node.getCareerRole() != null)
                .collect(Collectors.groupingBy(node -> node.getCareerRole().getCareerId()));

        for(User userSt: studentUsersPage.getContent()) {
            if (userSt.getRole() != UserRole.STUDENT) continue;
            Student student = studentMap.get(userSt.getUserId());
            if (student == null) continue;

            Map<String, Object> stInfo = new HashMap<>();
            stInfo.put("studentId", student.getUserId());
            stInfo.put("fullName", userSt.getFullName());
            stInfo.put("email", userSt.getEmail());
            stInfo.put("university", student.getUniversity() != null ? student.getUniversity().getName() : null);
            stInfo.put("avatar", userSt.getAvatarUrl());

            CareerRole careerRole = student.getCareerRole();
            if (careerRole != null) {
                stInfo.put("careerPath", careerRole.getCareerName());
                int totalNodeCompleted = studentProgressRepository.findRoadmapTotalNodeCompletedByCareerIdAndStudentId(careerRole.getCareerId(), student.getUserId());
                List<SkillNode> nodes = nodesByCareerId.getOrDefault(careerRole.getCareerId(), new ArrayList<>());
                int progress = nodes.isEmpty() ? 0 : totalNodeCompleted * 100 / nodes.size();
                stInfo.put("roadmapProgress", progress);
            } else {
                stInfo.put("careerPath", null);
                stInfo.put("roadmapProgress", 0);
            }
            stInfos.add(stInfo);
        }

        /* --- OLD N+1 CODE COMMENTED OUT ---
        for(User userSt: studentUsersPage.getContent()) {
            if (userSt.getRole() != UserRole.STUDENT) continue;
            Student student = studentRepository.findByUserId(userSt.getUserId());
            if (student == null) continue;

            Map<String, Object> stInfo = new HashMap<>();
            stInfo.put("studentId", student.getUserId());
            stInfo.put("fullName", userSt.getFullName());
            stInfo.put("email", userSt.getEmail());
            stInfo.put("university", student.getUniversity() != null ? student.getUniversity().getName() : null);
            stInfo.put("avatar", userSt.getAvatarUrl());

            CareerRole careerRole = student.getCareerRole();
            if (careerRole != null) {
                stInfo.put("careerPath", careerRole.getCareerName());
                int totalNodeCompleted = studentProgressRepository.findRoadmapTotalNodeCompletedByCareerIdAndStudentId(careerRole.getCareerId(), student.getUserId());
                List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerRole.getCareerId());
                int progress = nodes.isEmpty() ? 0 : totalNodeCompleted * 100 / nodes.size();
                stInfo.put("roadmapProgress", progress);
            } else {
                stInfo.put("careerPath", null);
                stInfo.put("roadmapProgress", 0);
            }
            stInfos.add(stInfo);
        }
        */

        Page<Map<String, Object>> mappedPage = new PageImpl<>(stInfos, pageable, studentUsersPage.getTotalElements());
        return MentorResponse.builder().students(mappedPage).build();
    }

    @Transactional
    public MentorResponse submitFeedback(CreateFeedbackRequest request) {
        User sender = getAuthenticatedMentor();
        User receiver = userRepository.findByUserId(request.getReceiverId());
        if (receiver == null) {
            throw new ResourceNotFoundException("Student not found");
        }

        Feedback feedback = new Feedback();
        feedback.setSender(sender);
        feedback.setReceiver(receiver);
        feedback.setSenderName(sender.getFullName());
        feedback.setContent(request.getContent());
        feedback.setType(request.getType());
        feedback = feedbackRepository.save(feedback);

        // Auto-resolve pending review requests from this student to this mentor
        List<PortfolioReviewRequest> pendingRequests = reviewRequestRepository.findByMentor_UserIdAndStatus(sender.getUserId(), ReviewStatus.PENDING, Pageable.unpaged()).getContent();
        for (PortfolioReviewRequest req : pendingRequests) {
            if (req.getStudent().getUserId().equals(receiver.getUserId())) {
                req.setStatus(ReviewStatus.REVIEWED);
                req.setResolvedAt(LocalDateTime.now());
                reviewRequestRepository.save(req);
            }
        }

        return MentorResponse.builder().feedback(feedback).build();
    }
}
