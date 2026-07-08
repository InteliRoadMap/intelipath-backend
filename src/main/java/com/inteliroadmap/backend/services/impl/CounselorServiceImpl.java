package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.RoadmapProgressCalculator;
import com.inteliroadmap.backend.domain.dto.request.ExportStudentListRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentInfoProjection;
import com.inteliroadmap.backend.domain.dto.response.UpdateProfileResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.CounselorMapper;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.services.CounselorService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;

/**
 * Implementation of CounselorService for academic counselors.
 * Provides operations for tracking student progress, managing feedback,
 * gathering career statistics, and updating counselor profiles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CounselorServiceImpl implements CounselorService {

    private final CounselorMapper counselorMapper;
    private final RoadmapProgressCalculator roadmapProgressCalculator;
    private final CareerRoleRepository careerRoleRepository;
    private final FeedbackRepository feedbackRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AcademicCounselorRepository academicCounselorRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final UniversityRepository universityRepository;


    /**
     * Retrieves the currently authenticated user based on the security context.
     *
     * @return the authenticated User entity
     * @throws ResourceNotFoundException if the user cannot be found
     */
    private User getAuthenticatedUser() {
        // Get the authenticated email and look up the corresponding user entity
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found from token");
        }
        return user;
    }

    /**
     * Retrieves the authenticated counselor profile. If one does not exist for the
     * current user, a new default profile is created.
     *
     * @return the AcademicCounselor entity
     */
    private AcademicCounselor getAuthenticatedCounselor() {
        // Identify the current user and check if they already have an AcademicCounselor profile
        User user = getAuthenticatedUser();
        AcademicCounselor counselor = academicCounselorRepository.findById(user.getUserId()).orElse(null);
        
        // If missing, auto-generate a new counselor profile to ensure consistency
        if (counselor == null) {
            log.info("CounselorServiceImpl: Counselor profile not found. Creating a new one for user: {}", user.getEmail());
            counselor = AcademicCounselor.builder().userId(user.getUserId()).build();
            counselor = academicCounselorRepository.save(counselor);
        }
        return counselor;
    }

    /**
     * Calculates statistics on the number of students enrolled in each career path.
     *
     * @return the CounselorResponse containing total students and breakdown by career
     */
    @Transactional
    @Override
    public CounselorResponse getCareerStatistics() {
        log.info("CounselorServiceImpl: Get careers statistic request received");
        getAuthenticatedCounselor();

        // Initialize a map to hold the count of students per career
        Map<String, Integer> careerStatistics = new HashMap<>();

        int total = 0;
        // Fetch all possible career roles
        List<CareerRole> careers = careerRoleRepository.findAll();

        // Iterate through each career and count the number of students assigned to it
        for(CareerRole career: careers) {
            List<Student> students = studentRepository.findByCareerRole(career);
            int number =  students.size();
            if(number > 0) {
                careerStatistics.put(career.getCareerName(), number);
            }
            total += number;
        }

        log.info("CounselorServiceImpl: Careers statistic retrieval successful");
        return counselorMapper.toRoadmapStatisticResponse(total, careerStatistics);
    }

    /**
     * Retrieves the count of students missing specific skills for a given career path.
     *
     * @param searchName the name of the career to search for
     * @return the CounselorResponse with missing skills data
     * @throws ResourceNotFoundException if no matching career is found
     * @throws ResponseStatusException if multiple careers match the search
     */
    @Transactional
    @Override
    public CounselorResponse getStudentsMissingSkills(String searchName) {
        log.info("CounselorServiceImpl: Get students skill gap of a career request received");
        getAuthenticatedCounselor();

        // Look up careers matching the provided search name (case-insensitive)
        List<CareerRole> matchingCareers = careerRoleRepository
                .findByCareerNameContainingIgnoreCase(searchName);
        
        // Enforce exact or distinct match by checking the number of results
        if (matchingCareers.isEmpty()) {
            throw new ResourceNotFoundException("No career found matching your search.");
        } else if (matchingCareers.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple careers found. Please type a more specific search.");
        }
        CareerRole matchedCareer = matchingCareers.getFirst();

        // Fetch raw data containing missing skill names and their occurrence counts
        List<Object[]> missingSkillsData = studentSkillRepository
                .findMissingSkillsByCareerId(matchedCareer.getCareerId());

        // Convert the raw object arrays into a strongly-typed map
        Map<String, Integer> totalMissingSkills = new HashMap<>();
        for (Object[] row : missingSkillsData) {
            String skillName = (String) row[0];
            Integer count = ((Number) row[1]).intValue();

            totalMissingSkills.put(skillName, count);
        }

        int totalStudent = studentRepository.findByCareerRole(matchedCareer).size();

        log.info("CounselorServiceImpl: Get students skill gap of a career successfully");
        return counselorMapper
                .toMissingSkillsResponse(totalStudent, totalMissingSkills, matchedCareer.getCareerName());
    }

    /**
     * Retrieves all feedback messages sent to the authenticated counselor.
     *
     * @return the CounselorResponse containing a list of received feedbacks
     */
    @Transactional
    @Override
    public CounselorResponse getAllFeedbacksSentByMe() {
        log.info("CounselorServiceImpl: Get feedback request received");
        // Identify the counselor and fetch all feedbacks where they are the receiver
        AcademicCounselor counselor = getAuthenticatedCounselor();
        User me = userRepository.findByUserId(counselor.getUserId());

        List<Feedback> feedbacks = feedbackRepository.findBySender_UserIdOrderByCreatedAtDesc(me.getUserId());

        log.info("CounselorServiceImpl: Get feedback successfully");
        return counselorMapper.toGetFeedbacksResponse(feedbacks, feedbacks.size());
    }

    /**
     * Retrieves paginated information about students associated with the counselor's university.
     *
     * @param search an optional search term to filter students
     * @param page the page number to retrieve
     * @param size the number of records per page
     * @return the CounselorResponse containing a paginated list of student information
     */
    @Transactional
    @Override
    public CounselorResponse getStudentInfos(String search, int page, int size) {
        log.info("CounselorServiceImpl: Get students info request received");
        if (search == null) search = "";

        // Filter students to only include those at the counselor's university
        AcademicCounselor counselor = getAuthenticatedCounselor();
        if (counselor.getUniversity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Counselor university is not configured.");
        }

        // Offset = page * size | page count begins form 0
        // Ex: Load page 3 with size = 10 --> Offset = 30
        // Pageable get Student from (Offset + 1) to (Offset + size)
        Pageable pageable = PageRequest.of(page, size);
        // Fetch the specific page of students from the database using projection
        Page<StudentInfoProjection> studentPage = studentRepository.findStudentInfos(search, counselor.getUniversity().getUniversityId(), pageable);
        
        List<Map<String, Object>> stInfos = new ArrayList<>();

        // Construct a summary object for each student on the current page using projection data
        for(StudentInfoProjection projection: studentPage.getContent()) {
            Map<String, Object> stInfo = new HashMap<>();
            stInfo.put("studentId", projection.getStudentId());
            stInfo.put("fullName", projection.getFullName());
            stInfo.put("email", projection.getEmail());
            stInfo.put("university", projection.getUniversity());
            stInfo.put("careerPath", projection.getCareerName());

            stInfos.add(stInfo);
            log.info(stInfo.toString());
        }

        log.info("CounselorServiceImpl: Get students info successfully");
        return counselorMapper.toGetStudentInfos(stInfos, studentPage.getTotalPages(), studentPage.getNumber());
    }

    /**
     * Retrieves detailed statistics and feedback history for a specific student.
     *
     * @param studentId the UUID of the student
     * @return the CounselorResponse containing the student's progress, missing skills, and feedback logs
     */
    @Transactional
    @Override
    public CounselorResponse getStudentStatisticAndFeedback(UUID studentId) {
        log.info("CounselorServiceImpl: Getting student statistic and feedback...");

        AcademicCounselor counselor = getAuthenticatedCounselor();
        User me = userRepository.findByUserId(counselor.getUserId());
        User st = userRepository.findByUserId(studentId);
        Student student = studentRepository.findByUserId(studentId);

        // Weighted roadmap progress, shared formula with the student-facing views
        int progress = 0;
        if (student.getCareerRole() != null) {
            log.info("Student doesnt have career role.");
            progress = roadmapProgressCalculator.calculateProgress(
                    skillNodeRepository.findByCareerRole_CareerId(student.getCareerRole().getCareerId()),
                    studentProgressRepository.findByStudent_UserId(student.getUserId()));
        }

        // Fetch the list of skills the student has yet to acquire for their current career
        List<String> missingSkillNames = studentSkillRepository
                .findMissingSkillsByStudentIdAndCareerId(
                        student.getUserId(),
                        student.getCareerRole().getCareerId()
                );

        List<Feedback> feedbacks = feedbackRepository
                .findBySenderOrReceiverOrderByCreatedAtDesc(me.getUserId(), st.getUserId());

        log.info("CounselorServiceImpl: Get student statistic and feedback successfully");
        return counselorMapper.toGetStudentStatisticAndFeedback(progress, missingSkillNames, feedbacks);
    }

    /**
     * Retrieves the profile information of the currently authenticated counselor.
     *
     * @return the UpdateProfileResponse with user and counselor details
     */
    @Transactional
    @Override
    public UpdateProfileResponse getProfile() {
        log.info("CounselorServiceImpl: Getting counselor profile...");

        AcademicCounselor counselor = getAuthenticatedCounselor();
        User user = getAuthenticatedUser();

        log.info("CounselorServiceImpl: Get counselor profile successfully");
        return counselorMapper.toCrudProfileResponse(user, counselor);
    }

    /**
     * Updates the profile information of the currently authenticated counselor.
     *
     * @param request the object containing the fields to update (e.g., name, university, department)
     * @return the UpdateProfileResponse with updated user and counselor details
     */
    @Transactional
    @Override
    public UpdateProfileResponse updateProfile(UpdateProfileRequest request) {
        log.info("CounselorServiceImpl: Update profile request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        User user = getAuthenticatedUser();

        // Conditionally apply updates only for the fields provided in the request
        if(request.getFullName() != null) user.setFullName(request.getFullName());
        if(request.getYob() != null) user.setYob(request.getYob());
        if(request.getBio() != null) user.setBio(request.getBio());
        if(request.getUniversityId() != null) {
            University university = universityRepository.findById(request.getUniversityId())
                    .orElseThrow(() -> new ResourceNotFoundException("University not found"));
            counselor.setUniversity(university);
        }
        if(request.getDepartment() != null) counselor.setDepartment(request.getDepartment());

        user = userRepository.save(user);
        counselor = academicCounselorRepository.save(counselor);

        log.info("CounselorServiceImpl: Update profile successfully");
        return counselorMapper.toCrudProfileResponse(user, counselor);
    }

    @Transactional
    @Override
    public byte[] exportStudentList(ExportStudentListRequest request) {
        log.info("CounselorServiceImpl: Export student list request received");
        getAuthenticatedCounselor();

        List<UUID> studentIds = request.getStudentIds();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Student List");

            // Header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("University");
            header.createCell(3).setCellValue("Major");
            header.createCell(4).setCellValue("Skills");
            header.createCell(5).setCellValue("Github Profile");

            // Fetch all users and students at once
            Map<UUID, User> userMap = userRepository.findAllById(studentIds)
                    .stream().collect(toMap(User::getUserId, u -> u));
            Map<UUID, Student> studentMap = studentRepository.findAllById(studentIds)
                    .stream().collect(toMap(Student::getUserId, s -> s));

            // Fetch all skills at once
            List<Object[]> allSkills = studentSkillRepository.findSkillNamesByStudentIds(studentIds);
            Map<UUID, List<String>> skillMap = new HashMap<>();
            for (Object[] obj : allSkills) {
                UUID stId = (UUID) obj[0];
                String skillName = (String) obj[1];
                skillMap.computeIfAbsent(stId, k -> new ArrayList<>()).add(skillName);
            }

            // Body
            int rowNum = 1;
            for(UUID studentId: studentIds){
                User user = userMap.get(studentId);
                Student student = studentMap.get(studentId);
                List<String> skillNames = skillMap.getOrDefault(studentId, emptyList());

                if (user == null || student == null) continue;

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(user.getFullName());
                row.createCell(1).setCellValue(user.getEmail());
                row.createCell(2).setCellValue(student.getUniversity() != null ? student.getUniversity().getName() : "");
                row.createCell(3).setCellValue(student.getMajor());
                row.createCell(4).setCellValue(skillNames.toString().replace("[]",""));
                row.createCell(5).setCellValue(student.getGithubProfile());
            }

            // Auto size columns
            for (int i = 0; i < 6; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            return output.toByteArray();

        } catch (IOException e) {
            log.error("CounselorServiceImpl: Error while exporting student list", e);
        }
        return null;
    }
}
