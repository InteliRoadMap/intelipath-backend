package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.StudentAccounts;

import com.inteliroadmap.backend.components.RoadmapProgressCalculator;
import com.inteliroadmap.backend.domain.dto.request.ExportStudentListRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportStudentAccountsRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorDashboardResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CurriculumResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.projection.StudentInfoProjection;
import com.inteliroadmap.backend.domain.dto.response.student.UpdateProfileResponse;
import com.inteliroadmap.backend.domain.enums.AccountType;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.mappers.CounselorMapper;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.services.CounselorService;
import com.inteliroadmap.backend.services.PortfolioSlugService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
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
    private final FptCurriculumRepository fptCurriculumRepository;
    private final PasswordEncoder passwordEncoder;
    private final PortfolioSlugService portfolioSlugService;


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
            throw new UnauthorizedException("User not found from token");
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
    public CounselorDashboardResponse getCareerStatistics() {
        log.info("CounselorServiceImpl: Get careers statistic request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        String universityName = counselor.getUniversityName();

        // Initialize a map to hold the count of students per career
        Map<String, Integer> careerStatistics = new HashMap<>();

        int totalStudents = studentRepository.findByUniversityName(universityName).size();
        int noCareerStudents = totalStudents;
        // Fetch all possible career roles
        List<CareerRole> careers = careerRoleRepository.findAll();

        // Iterate through each career and count the number of students assigned to it
        for(CareerRole career: careers) {
            List<Student> students = studentRepository.findByCareerRoleAndUniversityName(career, universityName);
            if(!students.isEmpty()) careerStatistics.put(career.getCareerName(), students.size());

            noCareerStudents -= students.size();
        }

        if(noCareerStudents > 0) careerStatistics.put("Career path not selected", noCareerStudents);

        log.info("CounselorServiceImpl: Careers statistic retrieval successful");
        return counselorMapper.toRoadmapStatisticResponse(totalStudents, careerStatistics);
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
    public CounselorDashboardResponse getStudentsMissingSkills(String searchName) {
        log.info("CounselorServiceImpl: Get students skill gap of a career request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        String universityName = counselor.getUniversityName();

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

        int totalStudent = studentRepository.findByCareerRoleAndUniversityName(matchedCareer, universityName).size();

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
    public CounselorDashboardResponse getAllFeedbacksSentByMe() {
        log.info("CounselorServiceImpl: Get feedback request received");
        // Identify the counselor and fetch all feedbacks where they are the receiver
        AcademicCounselor counselor = getAuthenticatedCounselor();
        User me = userRepository.findByUserId(counselor.getUserId());

        List<Feedback> feedbacks = feedbackRepository.findBySender_UserIdOrderByCreatedAtDesc(me.getUserId());

        log.info("CounselorServiceImpl: Get feedback successfully");
        return counselorMapper.toGetFeedbacksResponse(feedbacks, feedbacks.size());
    }

    /**
     * Retrieves paginated information about students of the counselor's own account type.
     *
     * @param search an optional search term to filter students
     * @param page the page number to retrieve
     * @param size the number of records per page
     * @return the CounselorResponse containing a paginated list of student information
     */
    @Transactional
    @Override
    public CounselorFeedbackResponse getStudentInfos(String search, int page, int size, String career) {
        log.info("CounselorServiceImpl: Get students info request received");
        if (search == null) search = "";
        if (career == null) career = "";

        // Students are scoped to the counselor's own account type, so an FPT counselor
        // sees every FPT student and no OTHER ones.
        AccountType accountType = getAuthenticatedUser().getAccountType();

        // Offset = page * size | page count begins form 0
        // Ex: Load page 3 with size = 10 --> Offset = 30
        // Pageable get Student from (Offset + 1) to (Offset + size)
        Pageable pageable = PageRequest.of(page, size);
        // Fetch the specific page of students from the database using projection
        Page<StudentInfoProjection> studentPage = studentRepository.findStudentInfos(search, career, accountType, pageable);
        
        AcademicCounselor counselor = getAuthenticatedCounselor();
        List<String> careers = studentRepository.findDistinctCareerNamesByUniversityName(counselor.getUniversityName());

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
        return counselorMapper.toGetStudentInfos(stInfos, careers, studentPage.getTotalPages(), studentPage.getNumber());
    }

    /**
     * Retrieves detailed statistics and feedback history for a specific student.
     *
     * @param studentId the UUID of the student
     * @return the CounselorResponse containing the student's progress, missing skills, and feedback logs
     */
    @Transactional
    @Override
    public CounselorFeedbackResponse getStudentStatisticAndFeedback(UUID studentId) {
        log.info("CounselorServiceImpl: Getting student statistic and feedback...");

        AcademicCounselor counselor = getAuthenticatedCounselor();
        User me = userRepository.findByUserId(counselor.getUserId());
        User st = userRepository.findByUserId(studentId);
        Student student = studentRepository.findByUserId(studentId);
        if (student == null) {
            throw new ResourceNotFoundException("Student not found");
        }

        // A student who has not selected a career yet has no roadmap/skill data to report.
        UUID careerId = student.getCareerRole() != null ? student.getCareerRole().getCareerId() : null;

        // Weighted roadmap progress, shared formula AND shared denominator with the
        // student-facing views. Counting the whole career here while the student is
        // shown only published nodes puts two different percentages on one person,
        // and the student is the one who has to explain the gap.
        int progress = careerId == null ? 0 : roadmapProgressCalculator.calculateProgress(
                skillNodeRepository.findPublishedForCareer(careerId),
                studentProgressRepository.findByStudent_UserId(student.getUserId()));

        // Fetch the list of skills the student has yet to acquire for their current career
        List<String> missingSkillNames = careerId == null ? List.of() : studentSkillRepository
                .findMissingSkillsByStudentIdAndCareerId(
                        student.getUserId(),
                        careerId
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
        if(request.getDepartment() != null) counselor.setDepartment(request.getDepartment());
        if(request.getAdmissionDate() != null) counselor.setAdmissionDate(request.getAdmissionDate());

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
            header.createCell(4).setCellValue("Target Career");
            header.createCell(5).setCellValue("Learning Progress");
            header.createCell(6).setCellValue("Skills");
            header.createCell(7).setCellValue("Github Profile");

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

                // Same denominator as the student's own roadmap - see the note above.
                int progress = roadmapProgressCalculator.calculateProgress(
                        skillNodeRepository.findPublishedForCareer(student.getCareerRole().getCareerId()),
                        studentProgressRepository.findByStudent_UserId(student.getUserId()));

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(user.getFullName());
                row.createCell(1).setCellValue(user.getEmail());
                row.createCell(2).setCellValue(student.getUniversityName() != null ? student.getUniversityName() : "");
                row.createCell(3).setCellValue(student.getMajor());
                row.createCell(4).setCellValue(student.getCareerRole().getCareerName());
                row.createCell(5).setCellValue(progress + "%");
                row.createCell(6).setCellValue(skillNames.toString());
                row.createCell(7).setCellValue(student.getGithubProfile());
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

    @Transactional
    @Override
    public CurriculumResponse getCurriculums() {
        log.info("CounselorServiceImpl: Get curriculums request received");

        List<String> curriculums = new ArrayList<>();
        List<FptCurriculum> FptCurriculums = fptCurriculumRepository.findAll();
        if (FptCurriculums.isEmpty()) throw new ResourceNotFoundException("Curriculums not found");
        else {
            for (FptCurriculum fptCurriculum : FptCurriculums) {
                curriculums.add(fptCurriculum.getCode());
            }
        }
        log.info("CounselorServiceImpl: Get curriculums  successfully");
        return counselorMapper.toCurriculumResponse(curriculums);
    }

    @Transactional
    @Override
    public void checkStudentEmail(String email) {
        log.info("CounselorServiceImpl: Check student email request received");
        User user = userRepository.findByEmail(email);
        if (user != null) throw new BadRequestException("Student email already exists");
        log.info("CounselorServiceImpl: Student email is valid to create account");
    }

    @Transactional
    @Override
    public byte[] importStudentAccounts(ImportStudentAccountsRequest request) {
        log.info("CounselorServiceImpl: Import student accounts request received");
        AcademicCounselor counselor = getAuthenticatedCounselor();
        User userC = userRepository.findByUserId(counselor.getUserId());

        List<String[]> createdAccounts = new ArrayList<>();
        List<StudentAccounts> studentAccounts = request.getAccounts();
        for (StudentAccounts account: studentAccounts) {
            User stUser = userRepository.findByEmail(account.getEmail());

            FptCurriculum curriculum = fptCurriculumRepository.findByCode(account.getCurriculum()).orElse(null);
            if (curriculum == null) throw new ResourceNotFoundException("Curriculum not found");

            String username;

            if (stUser != null) {
                log.info("CounselorServiceImpl: User account for student {} found, skip importing", account.getEmail());
                username = stUser.getUsername();
            } else {
                // Generate username as "Curriculum program + @ + email"
                username = curriculum.getProgram() + "@" + account.getEmail().split("@")[0];

                // Generates random password from the last set of strings in UUID (12 char)
                String randomPassword = UUID.randomUUID().toString().split("-")[4];

                createdAccounts.add(new String[]{account.getFullName(), account.getEmail(), username, randomPassword});

                stUser = User.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(randomPassword))
                        .email(account.getEmail())
                        .fullName(account.getFullName())
                        .accountType(userC.getAccountType())
                        .build();
                stUser = userRepository.save(stUser);
            }

            Student student = studentRepository.findByUserId(stUser.getUserId());
            if (student != null) log.info("CounselorServiceImpl: Student account for student {} found, skip importing", username);
            else {
                String uniName = counselor.getUniversityName();

                student = Student.builder()
                        .userId(stUser.getUserId())
                        .universityName(uniName)
                        .admissionDate(account.getAdmissionDate())
                        .major(account.getMajor())
                        .fptCurriculumId(curriculum.getId())
                        .portfolioSlug(portfolioSlugService.allocateFor(stUser))
                        .build();
                studentRepository.save(student);
            }
        }
        log.info("CounselorServiceImpl: {} Student accounts successfully imported", createdAccounts.size());

        log.info("CounselorServiceImpl: Creating Student account list");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Student Accounts");

            // Header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Student Name");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Account Username");
            header.createCell(3).setCellValue("Temporary Password");

            // Body
            int rowNum = 1;
            for(String[] acc : createdAccounts) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(acc[0]);
                row.createCell(1).setCellValue(acc[1]);
                row.createCell(2).setCellValue(acc[2]);
                row.createCell(3).setCellValue(acc[3]);
            }

            // Auto size columns
            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            log.info("CounselorServiceImpl: Student account list returned");
            return output.toByteArray();

        } catch (IOException e) {
            log.error("CounselorServiceImpl: Error while exporting student account list", e);
        }
        return null;
    }
}
