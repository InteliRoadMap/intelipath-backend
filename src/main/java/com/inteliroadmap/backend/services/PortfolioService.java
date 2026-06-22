package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateSlugRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.mappers.PortfolioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final AuthenticatedStudentService AuthenticatedStudentService;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioConfigRepository portfolioConfigRepository;
    private final PortfolioProjectRepository portfolioProjectRepository;
    private final StudentEducationRepository studentEducationRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final com.inteliroadmap.backend.repositories.PortfolioReviewRequestRepository portfolioReviewRequestRepository;

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio() {
        Student student = AuthenticatedStudentService.getRequiredStudent();
        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("[PortfolioService] User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        PortfolioConfig config = portfolioConfigRepository.findByUser(student);
        List<StudentSkill> skills = studentSkillRepository.findByStudent(student);
        List<PortfolioProject> projects = portfolioProjectRepository.findByUser(user);
        List<StudentEducation> education = studentEducationRepository.findByUser(student);

        return portfolioMapper.toPortfolioResponse(user, student, config, skills, projects, education);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolioBySlug(String slug) {
        Optional<Student> studentOpt = studentRepository.findByPortfolioSlug(slug);
        if (studentOpt.isEmpty()) {
            log.error("[PortfolioService] Portfolio not found for slug: {}", slug);
            throw new ResourceNotFoundException("Portfolio not found for slug: " + slug);
        }
        Student student = studentOpt.get();

        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("[PortfolioService] User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        PortfolioConfig config = portfolioConfigRepository.findByUser(student);
        List<StudentSkill> skills = studentSkillRepository.findByStudent(student);
        List<PortfolioProject> projects = portfolioProjectRepository.findByUser(user);
        List<StudentEducation> education = studentEducationRepository.findByUser(student);

        return portfolioMapper.toPortfolioResponse(user, student, config, skills, projects, education);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolioByStudentId(UUID studentId) {
        Optional<Student> studentOpt = studentRepository.findById(studentId);
        if (studentOpt.isEmpty()) {
            log.error("[PortfolioService] Student not found for ID: {}", studentId);
            throw new ResourceNotFoundException("Student not found for ID: " + studentId);
        }
        Student student = studentOpt.get();

        Optional<User> userOpt = userRepository.findById(studentId);
        if (userOpt.isEmpty()) {
            log.error("[PortfolioService] User not found for ID: {}", studentId);
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        PortfolioConfig config = portfolioConfigRepository.findByUser(student);
        List<StudentSkill> skills = studentSkillRepository.findByStudent(student);
        List<PortfolioProject> projects = portfolioProjectRepository.findByUser(user);
        List<StudentEducation> education = studentEducationRepository.findByUser(student);

        return portfolioMapper.toPortfolioResponse(user, student, config, skills, projects, education);
    }

    @Transactional
    public PortfolioResponse upsertPortfolio(PortfolioUpsertRequest request) {
        Student student = AuthenticatedStudentService.getOrCreateStudentForUpdate();
        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("[PortfolioService] User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        syncPortfolioConfig(request.getConfig(), student);
        syncPortfolioProjects(request.getProjects(), user);
        syncPortfolioEducation(request.getEducation(), student);

        return getPortfolio();
    }

    private void syncPortfolioConfig(PortfolioUpsertRequest.PortfolioConfigRequest configRequest, Student student) {
        if (configRequest == null) return;
        PortfolioConfig config = portfolioConfigRepository.findByUser(student);
        if (config == null) {
            config = PortfolioConfig.builder().user(student).build();
        }
        config.setTheme(configRequest.getTheme());
        config.setThemeColors(configRequest.getThemeColors());
        config.setFonts(configRequest.getFonts());
        config.setHeroSection(configRequest.getHeroSection());
        config.setSkillsSection(configRequest.getSkillsSection());
        portfolioConfigRepository.save(config);
    }

    private void syncPortfolioProjects(List<PortfolioUpsertRequest.PortfolioProjectRequest> projectRequests, User user) {
        if (projectRequests == null) return;
        portfolioProjectRepository.deleteByUser(user);
        List<PortfolioProject> newProjects = portfolioMapper.toPortfolioProjects(projectRequests, user);
        portfolioProjectRepository.saveAll(newProjects);
    }

    private void syncPortfolioEducation(List<PortfolioUpsertRequest.StudentEducationRequest> educationRequests, Student student) {
        if (educationRequests == null) return;
        studentEducationRepository.deleteByUser(student);
        List<StudentEducation> newEducation = portfolioMapper.toStudentEducationList(educationRequests, student);
        studentEducationRepository.saveAll(newEducation);
    }

    @Transactional
    public void requestReview(com.inteliroadmap.backend.domain.dto.request.RequestReviewRequest request) {
        Student student = AuthenticatedStudentService.getRequiredStudent();
        User mentor = userRepository.findByEmail(request.getEmail());
        if (mentor == null || mentor.getRole() != com.inteliroadmap.backend.domain.enums.UserRole.MENTOR) {
            throw new ResourceNotFoundException("Mentor not found with email: " + request.getEmail());
        }

        boolean exists = portfolioReviewRequestRepository.existsByStudent_UserIdAndMentor_UserIdAndStatus(
                student.getUserId(),
                mentor.getUserId(),
                com.inteliroadmap.backend.domain.enums.ReviewStatus.PENDING
        );

        if (exists) {
            throw new IllegalArgumentException("A pending review request already exists for this mentor.");
        }

        com.inteliroadmap.backend.domain.entity.PortfolioReviewRequest reviewReq = new com.inteliroadmap.backend.domain.entity.PortfolioReviewRequest();
        reviewReq.setStudent(userRepository.findById(student.getUserId()).orElseThrow());
        reviewReq.setMentor(mentor);
        reviewReq.setStatus(com.inteliroadmap.backend.domain.enums.ReviewStatus.PENDING);
        
        portfolioReviewRequestRepository.save(reviewReq);
    }
}
