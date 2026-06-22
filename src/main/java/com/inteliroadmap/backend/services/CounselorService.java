package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.UpdateProfileResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.CounselorMapper;
import com.inteliroadmap.backend.mappers.DatasetMapper;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CounselorService {

    private final CounselorMapper counselorMapper;
    private final CareerRoleRepository careerRoleRepository;
    private final FeedbackRepository feedbackRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AcademicCounselorRepository academicCounselorRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final UniversityRepository universityRepository;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found from token");
        }
        return user;
    }

    private AcademicCounselor getAuthenticatedCounselor() {
        User user = getAuthenticatedUser();
        AcademicCounselor counselor = academicCounselorRepository.findById(user.getUserId()).orElse(null);
        if (counselor == null) {
            log.info("Counselor profile not found. Creating a new one for user: {}", user.getEmail());
            counselor = AcademicCounselor.builder().userId(user.getUserId()).build();
            counselor = academicCounselorRepository.save(counselor);
        }
        return counselor;
    }

    @Transactional
    public CounselorResponse getCareerStatistics() {
        log.info("Get careers statistic request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        UUID counselorUniversityId = counselor.getUniversity() != null ? counselor.getUniversity().getUniversityId() : null;

        Map<String, Integer> careerStatistics = new HashMap<>();
        int total = 0;
        List<CareerRole> careers = careerRoleRepository.findAll();

        if (counselorUniversityId != null) {
            for(CareerRole career: careers) {
                List<Student> students = studentRepository.findByCareerRoleAndUniversity_UniversityId(career, counselorUniversityId);
                int number =  students.size();
                if(number > 0) {
                    careerStatistics.put(career.getCareerName(), number);
                }
                total += number;
            }
        }

        log.info("Careers statistic retrieval successful");
        return counselorMapper.toRoadmapStatisticResponse(total, careerStatistics);
    }

    @Transactional
    public CounselorResponse getStudentsMissingSkills(String searchName) {
        log.info("Get students skill gap of a career request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        UUID counselorUniversityId = counselor.getUniversity() != null ? counselor.getUniversity().getUniversityId() : null;

        Map<String, Integer> totalMissingSkills = new HashMap<>();
        int totalStudent = 0;
        String previousStudentId = "";

        List<DatasetMapper> missingSkillsData;

        List<CareerRole> matchingCareers = careerRoleRepository.findByCareerNameContainingIgnoreCase(searchName);
        if (matchingCareers.isEmpty()) {
            throw new ResourceNotFoundException("No career found matching your search.");
        } else if (matchingCareers.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple careers found. Please type a more specific search.");
        }

        CareerRole matchedCareer = matchingCareers.getFirst();
        if (counselorUniversityId != null) {
            missingSkillsData = studentSkillRepository.findMissingSkillsByCareerIdAndUniversityId(matchedCareer.getCareerId(), counselorUniversityId);
        } else {
            missingSkillsData = new ArrayList<>();
        }

        for(DatasetMapper row : missingSkillsData) {
            String skillName = row.getSkillName();
            Integer number =  totalMissingSkills.get(skillName);

            if(number == null) {
                totalMissingSkills.put(skillName, 1);
            } else {
                number += 1;
                totalMissingSkills.put(skillName, number);
            }

            String currentStudentId = row.getStudentId();
            if(!previousStudentId.equals(currentStudentId)) {
                totalStudent += 1;
                previousStudentId = currentStudentId;
            }
        }

        log.info("Get students skill gap of a career successfully");
        return counselorMapper
                .toMissingSkillsResponse(totalStudent, totalMissingSkills, matchedCareer.getCareerName());
    }

    @Transactional
    public CounselorResponse getStudentInfos(String search) {
        log.info("Get students info request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        UUID counselorUniversityId = counselor.getUniversity() != null ? counselor.getUniversity().getUniversityId() : null;

        List<Student> students;
        if (search == null || search.trim().isEmpty()) {
            if (counselorUniversityId != null) {
                students = studentRepository.findByUniversity_UniversityId(counselorUniversityId);
            } else {
                students = new ArrayList<>();
            }
        } else {
            if (counselorUniversityId != null) {
                students = studentRepository.searchStudentsInfoByUniversity(search, counselorUniversityId);
            } else {
                students = new ArrayList<>();
            }
        }
        List<Map<String, Object>> stInfos = new ArrayList<>();

        // --- OPTIMIZED N+1 QUERY ---
        // Pre-fetch all user info to avoid N+1 queries
        List<UUID> studentUserIds = students.stream().map(Student::getUserId).collect(Collectors.toList());
        List<User> userList = userRepository.findAllById(studentUserIds);
        Map<UUID, User> userMap = userList.stream().collect(Collectors.toMap(User::getUserId, u -> u));

        // Pre-fetch skill nodes by careerId
        List<UUID> careerIds = students.stream()
                .filter(s -> s.getCareerRole() != null)
                .map(s -> s.getCareerRole().getCareerId())
                .distinct()
                .collect(Collectors.toList());
        List<SkillNode> allNodes = skillNodeRepository.findAll();
        Map<UUID, List<SkillNode>> nodesByCareerId = allNodes.stream()
                .filter(node -> node.getCareerRole() != null)
                .collect(Collectors.groupingBy(node -> node.getCareerRole().getCareerId()));

        for(Student student: students) {
            User userSt = userMap.get(student.getUserId());
            if (userSt == null || userSt.getRole() != UserRole.STUDENT) continue;

            Map<String, Object> stInfo = new HashMap<>();
            stInfo.put("studentId", student.getUserId());
            stInfo.put("fullName", userSt.getFullName());
            stInfo.put("email", userSt.getEmail());
            stInfo.put("university", student.getUniversity() != null ? student.getUniversity().getName() : null);

            CareerRole careerRole = student.getCareerRole();
            if (careerRole == null) {
                stInfo.put("careerPath", null);
                stInfo.put("roadmapProgress", 0);
                stInfo.put("missingSkills", new ArrayList<>());
            } else {
                stInfo.put("careerPath", careerRole.getCareerName());

                // Fix N+1 here by using the mapped lists
                int totalNodeCompleted = studentProgressRepository
                        .findRoadmapTotalNodeCompletedByCareerIdAndStudentId(careerRole.getCareerId(), student.getUserId());

                List<SkillNode> nodes = nodesByCareerId.getOrDefault(careerRole.getCareerId(), new ArrayList<>());

                // Keep the same rounding rule as the student's primary roadmap API.
                int progress = nodes.isEmpty() ? 0
                        : (int) Math.round(((double) totalNodeCompleted / nodes.size()) * 100);
                stInfo.put("roadmapProgress", progress);

                // For missing skills, keeping original query since it's complex to batch without modifying Repository
                List<DatasetMapper> missingSkills = studentSkillRepository
                        .findMissingSkillsByStudentIdAndCareerName(
                                student.getUserId(),
                                careerRole.getCareerName()
                        );
                List<String> missingSkillNames = missingSkills.stream()
                        .map(DatasetMapper::getSkillName).collect(Collectors.toList());
                stInfo.put("missingSkills", missingSkillNames);
            }

            stInfos.add(stInfo);
            // log.info(stInfos.toString()); // Remove log to avoid spamming
        }

        /* --- OLD N+1 CODE COMMENTED OUT ---
        for(Student student: students) {
            User userSt = userRepository.findByUserId(student.getUserId());
            if (userSt.getRole() != UserRole.STUDENT) continue;

            Map<String, Object> stInfo = new HashMap<>();
            stInfo.put("studentId", student.getUserId());
            stInfo.put("fullName", userSt.getFullName());
            stInfo.put("email", userSt.getEmail());
            stInfo.put("university", student.getUniversity() != null ? student.getUniversity().getName() : null);

            CareerRole careerRole = student.getCareerRole();
            if (careerRole == null) {
                stInfo.put("careerPath", null);
                stInfo.put("roadmapProgress", 0);
                stInfo.put("missingSkills", new ArrayList<>());
            } else {
                stInfo.put("careerPath", careerRole.getCareerName());

                int totalNodeCompleted = studentProgressRepository
                        .findRoadmapTotalNodeCompletedByCareerIdAndStudentId(careerRole.getCareerId(), student.getUserId());

                List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerRole.getCareerId());

                // Keep the same rounding rule as the student's primary roadmap API.
                int progress = nodes.isEmpty() ? 0
                        : (int) Math.round(((double) totalNodeCompleted / nodes.size()) * 100);
                stInfo.put("roadmapProgress", progress);

                List<DatasetMapper> missingSkills = studentSkillRepository
                        .findMissingSkillsByStudentIdAndCareerName(
                                student.getUserId(),
                                careerRole.getCareerName()
                        );
                List<String> missingSkillNames = missingSkills.stream()
                        .map(DatasetMapper::getSkillName).collect(Collectors.toList());
                stInfo.put("missingSkills", missingSkillNames);
            }

            stInfos.add(stInfo);
            log.info(stInfos.toString());
        }
        */

        log.info("Get students info successfully");
        return counselorMapper.getStudentInfos(stInfos);
    }

    @Transactional
    public CounselorResponse getAllFeedbacksSentToMe() {
        log.info("Get feedback request received");
        User counselorUser = getAuthenticatedUser();

        List<Feedback> feedbacks = feedbackRepository.findByReceiver(counselorUser);

        log.info("Get feedback successfully");
        return counselorMapper.toGetFeedbacksResponse(feedbacks, feedbacks.size());
    }

    @Transactional
    public CounselorResponse getFeedbacksHistoryWithStudent(UUID studentId) {
        log.info("Getting feedback history...");

        User counselorUser = getAuthenticatedUser();

        User student = userRepository.findByUserId(studentId);
        if (student == null) {
            throw new ResourceNotFoundException("Student not found");
        }

        List<Feedback> feedbacks = feedbackRepository
                .findBySenderOrReceiverOrderByCreateAtDesc(student, student);

        feedbacks.removeIf(f ->
                !(f.getSender().equals(counselorUser) && f.getReceiver().equals(student)) &&
                !(f.getSender().equals(student) && f.getReceiver().equals(counselorUser)));

        log.info("Get feedback history successfully");
        return counselorMapper.toGetFeedbacksResponse(feedbacks, feedbacks.size());
    }

    @Transactional
    public CounselorResponse createFeedback(CreateFeedbackRequest request) {
        log.info("Creating feedback...");

        User sender = getAuthenticatedUser();

        User receiver = userRepository.findByUserId(request.getReceiverId());
        if (receiver == null) {
            throw new ResourceNotFoundException("Receiver not found");
        }

        Feedback feedback = new Feedback();
        feedback.setSender(sender);
        feedback.setReceiver(receiver);
        feedback.setSenderName(sender.getFullName());
        feedback.setContent(request.getContent());
        feedback.setType(request.getType());

        feedback = feedbackRepository.save(feedback);

        log.info("New feedback created successfully");
        return counselorMapper.toCrudFeedbackResponse(feedback);
    }

    @Transactional
    public CounselorResponse deleteFeedback(java.util.UUID feedbackId) {
        log.info("Delete feedback request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        Feedback feedback = feedbackRepository.findById(feedbackId).orElse(null);
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        feedbackRepository.delete(feedback);
        return CounselorResponse.builder()
                .build();
    }

    @Transactional
    public CounselorResponse modifyFeedback(com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest request) {
        log.info("Modify feedback request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        Feedback feedback = feedbackRepository.findById(request.getFeedbackId()).orElse(null);
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        feedback.setContent(request.getContent());
        feedback.setType(com.inteliroadmap.backend.domain.enums.FeedbackType.valueOf(request.getType()));
        feedbackRepository.save(feedback);
        return CounselorResponse.builder()
                .build();
    }

    @Transactional
    public UpdateProfileResponse getProfile() {
        log.info("Getting counselor profile...");

        AcademicCounselor counselor = getAuthenticatedCounselor();
        User user = getAuthenticatedUser();

        log.info("Get counselor profile successfully");
        return counselorMapper.toCrudProfileResponse(user, counselor);
    }

    @Transactional
    public UpdateProfileResponse updateProfile(UpdateProfileRequest request) {
        log.info("Update profile request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        User user = getAuthenticatedUser();

        if(request.getFullName() != null) user.setFullName(request.getFullName());
        if(request.getYob() != null) user.setYob(request.getYob());
        if(request.getBio() != null) user.setBio(request.getBio());
        if(request.getUniversityId() != null) {
            University university = universityRepository.findById(request.getUniversityId()).orElse(null);
            if (university != null) counselor.setUniversity(university);
        }
        if(request.getDepartment() != null) counselor.setDepartment(request.getDepartment());

        user = userRepository.save(user);
        counselor = academicCounselorRepository.save(counselor);

        log.info("Update profile successfully");
        return counselorMapper.toCrudProfileResponse(user, counselor);
    }
}
