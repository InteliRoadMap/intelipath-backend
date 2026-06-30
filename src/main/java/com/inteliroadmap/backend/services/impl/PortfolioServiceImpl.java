package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.mappers.PortfolioMapper;
import com.inteliroadmap.backend.services.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of the {@link PortfolioService} interface.
 * Handles operations related to the student's portfolio, including retrieval and updates
 * of portfolio configurations, projects, education, and skills.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioServiceImpl implements PortfolioService {

    private final AuthenticatedStudentService AuthenticatedStudentService;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioConfigRepository portfolioConfigRepository;
    private final PortfolioProjectRepository portfolioProjectRepository;
    private final StudentEducationRepository studentEducationRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    /**
     * Retrieves the portfolio for the currently authenticated student.
     *
     * @return a {@link PortfolioResponse} containing the user's portfolio data
     * @throws ResourceNotFoundException if the associated user is not found
     */
    @Transactional(readOnly = true)
    @Override
    public PortfolioResponse getPortfolio() {
        // Retrieve the currently authenticated student
        Student student = AuthenticatedStudentService.getRequiredStudent();
        // Fetch the associated user entity using the student's user ID
        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("[PortfolioService] User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        // Fetch portfolio configuration, skills, projects, and education for the student
        PortfolioConfig config = portfolioConfigRepository.findByUserId(student.getUserId());
        List<StudentSkill> skills = studentSkillRepository.findByStudentId(student.getUserId());
        List<PortfolioProject> projects = portfolioProjectRepository.findByUserId(user.getUserId());
        List<StudentEducation> education = studentEducationRepository.findByUserId(student.getUserId());

        // Map the collected data into a PortfolioResponse object
        return portfolioMapper.toPortfolioResponse(user, student, config, skills, projects, education);
    }

    /**
     * Retrieves a portfolio by its unique slug.
     *
     * @param slug the unique slug identifying the portfolio
     * @return a {@link PortfolioResponse} containing the portfolio data
     * @throws ResourceNotFoundException if the portfolio or associated user is not found
     */
    @Transactional(readOnly = true)
    @Override
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

        PortfolioConfig config = portfolioConfigRepository.findByUserId(student.getUserId());
        List<StudentSkill> skills = studentSkillRepository.findByStudentId(student.getUserId());
        List<PortfolioProject> projects = portfolioProjectRepository.findByUserId(user.getUserId());
        List<StudentEducation> education = studentEducationRepository.findByUserId(student.getUserId());

        return portfolioMapper.toPortfolioResponse(user, student, config, skills, projects, education);
    }

    /**
     * Upserts (updates or inserts) the portfolio data for the currently authenticated student.
     *
     * @param request the {@link PortfolioUpsertRequest} containing the new portfolio data
     * @return a {@link PortfolioResponse} with the updated portfolio data
     * @throws ResourceNotFoundException if the associated user is not found
     */
    @Transactional
    @Override
    public PortfolioResponse upsertPortfolio(PortfolioUpsertRequest request) {
        // Get or create the authenticated student for updates
        Student student = AuthenticatedStudentService.getOrCreateStudentForUpdate();
        // Fetch the corresponding user entity
        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("[PortfolioService] User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        // Synchronize the various sections of the portfolio based on the request
        syncPortfolioConfig(request.getConfig(), student);
        syncPortfolioProjects(request.getProjects(), user);
        syncPortfolioEducation(request.getEducation(), student);

        // Return the fully updated portfolio
        return getPortfolio();
    }

    /**
     * Synchronizes the portfolio configuration for a given student.
     *
     * @param configRequest the configuration request containing theme and section preferences
     * @param student the {@link Student} whose configuration is being updated
     */
    private void syncPortfolioConfig(PortfolioUpsertRequest.PortfolioConfigRequest configRequest, Student student) {
        if (configRequest == null) return;
        PortfolioConfig config = portfolioConfigRepository.findByUserId(student.getUserId());
        if (config == null) {
            config = PortfolioConfig.builder().userId(student.getUserId()).build();
        }
        config.setTheme(configRequest.getTheme());
        config.setThemeColors(configRequest.getThemeColors());
        config.setFonts(configRequest.getFonts());
        config.setHeroSection(configRequest.getHeroSection());
        config.setSkillsSection(configRequest.getSkillsSection());
        portfolioConfigRepository.save(config);
    }

    /**
     * Synchronizes the portfolio projects for a given user.
     * Deletes existing projects and saves the new ones.
     *
     * @param projectRequests the list of project requests to save
     * @param user the {@link User} whose projects are being updated
     */
    private void syncPortfolioProjects(List<PortfolioUpsertRequest.PortfolioProjectRequest> projectRequests, User user) {
        if (projectRequests == null) return;
        portfolioProjectRepository.deleteByUserId(user.getUserId());
        List<PortfolioProject> newProjects = portfolioMapper.toPortfolioProjects(projectRequests, user);
        portfolioProjectRepository.saveAll(newProjects);
    }

    /**
     * Synchronizes the student's education history.
     * Deletes existing education records and saves the new ones.
     *
     * @param educationRequests the list of education requests to save
     * @param student the {@link Student} whose education records are being updated
     */
    private void syncPortfolioEducation(List<PortfolioUpsertRequest.StudentEducationRequest> educationRequests, Student student) {
        if (educationRequests == null) return;
        studentEducationRepository.deleteByUserId(student.getUserId());
        List<StudentEducation> newEducation = portfolioMapper.toStudentEducationList(educationRequests, student);
        studentEducationRepository.saveAll(newEducation);
    }


}
