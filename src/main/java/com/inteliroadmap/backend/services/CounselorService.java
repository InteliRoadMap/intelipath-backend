package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.GetSkillGapRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.CareerResponse;
import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.DatasetMapper;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CounselorService {

    private final CareerRoleRepository careerRoleRepository;
    private final FeedbackRepository feedbackRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AcademicCounselorRepository academicCounselorRepository;

    private AcademicCounselor getAuthenticatedCounselor() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found from token");
        }
        AcademicCounselor counselor = academicCounselorRepository.findByUser(user);
        if (counselor == null) {
            log.info("Counselor profile not found. Creating a new one for user: {}", email);
            counselor = AcademicCounselor.builder().user(user).build();
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
            careerStatistics.put(career.getCareerName(), number);

            total += number;
        }

        return CounselorResponse.builder()
                .total(total)
                .careerStatistics(careerStatistics)
                .build();
    }

    @Transactional
    public CounselorResponse getStudentsMissingSkills(String careerName) {
        log.info("Get students skill gap of a career request received");
        getAuthenticatedCounselor();

        Map<String, Integer> totalMissingSkills = new HashMap<>();
        int totalStudent = 0;
        String previousStudent = "";

        List<DatasetMapper> missingSkillsData = studentRepository
                .findMissingSkillsByCareerName(careerName);

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

        return CounselorResponse.builder()
                .total(totalStudent)
                .missingSkills(totalMissingSkills)
                .build();
    }

    @Transactional
    public CounselorResponse getFeedbackByStudent(String search) {
        log.info("Get feedback request received");
        getAuthenticatedCounselor();

        User user = userRepository
                .findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(search, search);

        if (user == null) {
            throw new ResourceNotFoundException("Student not found");
        }

        List<Feedback> feedbacks = feedbackRepository.findByReceiver(user);

        Student student = studentRepository.findByUser(user);
        if (student == null) {
            throw new ResourceNotFoundException("Student profile not found");
        }

        return CounselorResponse.builder()
                .feedbacks(feedbacks)
                .build();
    }

    @Transactional
    public CounselorResponse createFeedback(CreateFeedbackRequest request) {
        log.info("Create feedback request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();

        User sender = counselor.getUser();
        User receiver = userRepository.findByUserId(request.getReceiverId());

        Feedback feedback = new Feedback();
        feedback.setSender(sender);
        feedback.setReceiver(receiver);
        feedback.setSenderName(sender.getFullName());
        feedback.setContent(request.getContent());
        feedback.setType(request.getType());

        feedback = feedbackRepository.save(feedback);
        return CounselorResponse.builder()
                .feedback(feedback)
                .build();
    }

//    @Transactional
//    public CounselorResponse deleteFeedback(DeleteFeedbackRequest request) {
//        log.info("Delete feedback request received");
//        return null;
//    }

    @Transactional
    public CounselorResponse modifyFeedback(ModifyFeedbackRequest request) {
        log.info("Modify feedback request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        Feedback feedback = feedbackRepository.findByFeedbackId(request.getFeedbackId());
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        feedback.setContent(request.getContent());
        feedback.setType(request.getType());
        feedbackRepository.save(feedback);
        return CounselorResponse.builder()
                .feedback(feedback)
                .build();
    }
}
