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
        getAuthenticatedCounselor();

        Map<String, Integer> careerStatistics = new HashMap<>();

        int total = 0;
        List<CareerRole> careers = careerRoleRepository.findAll();

        for(CareerRole career: careers) {
            List<Student> students = studentRepository.findByCareerRole(career);
            int number =  students.size();
            if(number > 0) {
                careerStatistics.put(career.getCareerName(), number);
            }
            total += number;
        }

        log.info("Careers statistic retrieval successful");
        return counselorMapper.toRoadmapStatisticResponse(total, careerStatistics);
    }

    @Transactional
    public CounselorResponse getStudentsMissingSkills(String searchName) {
        log.info("Get students skill gap of a career request received");
        getAuthenticatedCounselor();

        Map<String, Integer> totalMissingSkills = new HashMap<>();
        int totalStudent = 0;
        String previousStudent = "";

        List<DatasetMapper> missingSkillsData;

        List<CareerRole> matchingCareers = careerRoleRepository.findByCareerNameContainingIgnoreCase(searchName);
        if (matchingCareers.isEmpty()) {
            throw new ResourceNotFoundException("No career found matching your search.");
        } else if (matchingCareers.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple careers found. Please type a more specific search.");
        }

        CareerRole matchedCareer = matchingCareers.getFirst();
        missingSkillsData = studentSkillRepository.findMissingSkillsByCareerId(matchedCareer.getCareerId());

        for(DatasetMapper row : missingSkillsData) {
            String skillName = row.getSkillName();
            Integer number =  totalMissingSkills.get(skillName);

            if(number == null) {
                totalMissingSkills.put(skillName, 1);
            } else {
                number += 1;
                totalMissingSkills.put(skillName, number);
            }

            String currentStudent = row.getFullName();
            if(!previousStudent.equals(currentStudent)) {
                totalStudent += 1;
                previousStudent = currentStudent;
            }
        }

        log.info("Get students skill gap of a career successfully");
        return counselorMapper
                .toMissingSkillsResponse(totalStudent, totalMissingSkills, matchedCareer.getCareerName());
    }

    @Transactional
    public CounselorResponse getStudentInfos(String search) {
        log.info("Get students info request received");
        List<Student> students;
        if (search == null || search.trim().isEmpty()) {
            students = studentRepository.findAll();
        } else {
            students = studentRepository.searchStudentsInfo(search);
        }
        List<Map<String, Object>> stInfos = new ArrayList<>();

        for(Student student: students) {
            User userSt = userRepository.findByUserId(student.getUserId());
            if (userSt.getRole() != UserRole.STUDENT) continue;

            Map<String, Object> stInfo = new HashMap<>();
            stInfo.put("studentId", student.getUserId());
            stInfo.put("fullName", userSt.getFullName());
            stInfo.put("email", userSt.getEmail());
            stInfo.put("university", student.getUniversity());

            CareerRole careerRole = student.getCareerRole();
            if (careerRole == null) {
                stInfo.put("careerPath", null);
                stInfo.put("roadmapProgress", 0);
                stInfo.put("missingSkills", new ArrayList<>());
            } else {
                stInfo.put("careerPath", careerRole.getCareerName());

                int totalNodeCompleted = studentProgressRepository
                        .findRoadmapTotalNodeCompletedByCareerId(careerRole.getCareerId());

                List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerRole.getCareerId());

                int progress = nodes.isEmpty() ? 0 : totalNodeCompleted * 100 / nodes.size();
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

//    @Transactional
//    public CounselorResponse deleteFeedback(DeleteFeedbackRequest request) {
//        log.info("Delete feedback request received");
//        return null;
//    }

//    @Transactional
//    public CounselorResponse modifyFeedback(ModifyFeedbackRequest request) {
//        log.info("Modify feedback request received");
//        AcademicCounselor counselor = getAuthenticatedCounselor();
//        Feedback feedback = feedbackRepository.findByFeedbackId(request.getFeedbackId());
//        if (feedback == null) {
//            throw new ResourceNotFoundException("Feedback not found");
//        }
//        feedback.setContent(request.getContent());
//        feedback.setType(request.getType());
//        feedbackRepository.save(feedback);
//        return CounselorResponse.builder()
//                .feedback(feedback)
//                .build();
//    }

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
        if(request.getUniversity() != null) counselor.setUniversity(request.getUniversity());
        if(request.getDepartment() != null) counselor.setDepartment(request.getDepartment());

        user = userRepository.save(user);
        counselor = academicCounselorRepository.save(counselor);

        log.info("Update profile successfully");
        return counselorMapper.toCrudProfileResponse(user, counselor);
    }
}
