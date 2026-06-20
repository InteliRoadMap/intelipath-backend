package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.dto.response.student.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.mappers.StudentMapper;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private static final int AI_TEXT_LIMIT = 80;

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final FeedbackRepository feedbackRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final SkillMapper skillMapper;
    private final StudentDashboardMapper studentDashboardMapper;
    private final StudentMapper studentMapper;
    private final CareerRoleRepository careerRoleRepository;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final UniversityRepository universityRepository;
    
    // RAG Dependencies
    private final SupabaseStorageService supabaseStorageService;
    private final PdfToMarkdownService pdfToMarkdownService;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public StudentResponse setupStudentProfile(com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest request) {
        log.info("Student Module: Setup Student Profile Request received");

        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();
        User user = userRepository.findByUserId(student.getUserId());
        if (user == null) {
            throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("User not found");
        }

        if (request.getUniversityId() != null) {
            University university = universityRepository.findById(request.getUniversityId()).orElse(null);
            if (university != null) {
                student.setUniversity(university);
            }
        }
        if (request.getYearOfAdmission() != null && !request.getYearOfAdmission().trim().isEmpty()) {
            student.setYearOfAdmission(LocalDate.parse(request.getYearOfAdmission()));
        }

        if (request.getMajor() != null) {
            student.setMajor(request.getMajor());
        }

        if (request.getCareerId() != null) {
            CareerRole career = careerRoleRepository.findByCareerId(request.getCareerId());
            if (career == null) {
                log.warn("Career role was not found: {}", request.getCareerId());
                throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("Career role not found");
            }
            student.setCareerRole(career);
        }

        boolean userChanged = false;
        if (request.getBio() != null) {
            user.setBio(request.getBio());
            userChanged = true;
        }
        if (request.getYob() != null && !request.getYob().trim().isEmpty()) {
            user.setYob(LocalDate.parse(request.getYob()));
            userChanged = true;
        }

        if (userChanged) {
            userRepository.save(user);
        }

        studentRepository.save(student);

        log.info("Student profile updated successfully for user: {}", user.getEmail());
        return studentMapper.toProfileResponse(student, user);
    }

    @Transactional
    public StudentResponse getStudentProfile() {
        log.info("Student profile retrieval request received");
        Student student = authenticatedStudentService.getRequiredStudent();
        User user = userRepository.findByUserId(student.getUserId());
        return studentMapper.toProfileResponse(student, user);
    }

    @Transactional
    public StudentResponse updateTargetCareer(UUID careerId) {
        log.info("Student target career update request received. careerId: {}", careerId);
        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();
        CareerRole career = careerRoleRepository.findByCareerId(careerId);
        if (career == null) {
            throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("Career role not found");
        }
        student.setCareerRole(career);
        studentRepository.save(student);
        User user = userRepository.findByUserId(student.getUserId());
        log.info("Student target career updated successfully. careerId: {}", careerId);
        return studentMapper.toProfileResponse(student, user);
    }

    /**
     * Compare the current student's selected skills with required career skills.
     *
     * @return SkillResponse containing selected, required, and missing skills
     */
    @Transactional
    public SkillResponse compareCurrentStudentSkills() {
        log.info("Student Dashboard Module: Comparing selected skills with required skills");

        Student student = getCurrentStudent();
        if (student == null) {
            return SkillResponse.builder().build();
        }

        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        if (student.getCareerRole() == null) {
            return SkillResponse.builder()
                    .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                    .build();
        }

        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        List<CareerRequiredSkill> missingRequiredSkills = filterMissingRequiredSkills(requiredSkills, selectedSkills);
        List<Skill> missingSkills = missingRequiredSkills.stream()
                .map(CareerRequiredSkill::getSkill)
                .toList();

        List<com.inteliroadmap.backend.domain.dto.response.RequiredSkillResponse> requiredSkillResponses = skillMapper.toRequiredSkillResponses(requiredSkills);
        
        // --- OPTIMIZED N+1 QUERY ---
        Set<UUID> selectedSkillIds = selectedSkills.stream()
                .filter(s -> s.getSkill() != null)
                .map(s -> s.getSkill().getSkillId())
                .collect(Collectors.toSet());
        List<SkillNode> allCareerNodes = skillNodeRepository.findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        List<StudentProgress> allStudentProgress = studentProgressRepository.findByStudent(student);
        Map<UUID, StudentProgress> progressMap = allStudentProgress.stream()
                .filter(p -> p.getSkillNode() != null)
                .collect(Collectors.toMap(p -> p.getSkillNode().getNodeId(), p -> p, (p1, p2) -> p1));

        Map<UUID, List<SkillNode>> nodesBySkillId = allCareerNodes.stream()
                .filter(node -> node.getSkillId() != null)
                .collect(Collectors.groupingBy(SkillNode::getSkillId));

        requiredSkillResponses.forEach(res -> {
            UUID resSkillId = res.getSkill().getSkillId();
            if (selectedSkillIds.contains(resSkillId)) {
                res.setProgress(100);
            } else {
                List<SkillNode> nodesForSkill = nodesBySkillId.getOrDefault(resSkillId, new ArrayList<>());
                if (nodesForSkill.isEmpty()) {
                    res.setProgress(0);
                } else {
                    int completedCount = 0;
                    for (SkillNode node : nodesForSkill) {
                        StudentProgress prog = progressMap.get(node.getNodeId());
                        if (prog != null && prog.getStatus() == RoadmapStepStatus.COMPLETED) {
                            completedCount++;
                        }
                    }
                    res.setProgress((int) Math.round((double) completedCount / nodesForSkill.size() * 100));
                }
            }
        });

        /* --- OLD N+1 CODE COMMENTED OUT ---
        requiredSkillResponses.forEach(res -> {
            requiredSkills.stream()
                .filter(r -> r.getSkill().getSkillId().equals(res.getSkill().getSkillId()))
                .findFirst()
                .ifPresent(r -> res.setProgress(calculateSkillProgress(student, r.getSkill())));
        });
        */

        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                .requiredSkills(requiredSkillResponses)
                .missingSkills(skillMapper.toSkillItemResponses(missingSkills))
                .build();
    }

    private Student getCurrentStudent() {
        return authenticatedStudentService.getOrCreateStudent();
    }

    public List<CareerRequiredSkill> findMissingRequiredSkills(Student student) {
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        return filterMissingRequiredSkills(requiredSkills, selectedSkills);
    }

    private List<CareerRequiredSkill> filterMissingRequiredSkills(
            List<CareerRequiredSkill> requiredSkills,
            List<StudentSkill> selectedSkills
    ) {
        Set<UUID> selectedSkillIds = selectedSkills.stream()
                .map(StudentSkill::getSkill)
                .filter(Objects::nonNull)
                .map(Skill::getSkillId)
                .collect(java.util.stream.Collectors.toSet());

        return requiredSkills.stream()
                .filter(requiredSkill -> requiredSkill.getSkill() != null)
                .filter(requiredSkill -> !selectedSkillIds.contains(requiredSkill.getSkill().getSkillId()))
                .toList();
    }

    public Integer calculateSkillProgress(Student student, Skill skill) {
        // Keeping this method signature for backward compatibility just in case it's used elsewhere
        if (studentSkillRepository.existsByStudentAndSkill(student, skill)) {
            return 100;
        }
        List<SkillNode> allNodesForSkill = skillNodeRepository.findBySkillIdAndCareerRole_CareerId(skill.getSkillId(), student.getCareerRole().getCareerId());
        if (allNodesForSkill.isEmpty()) {
            return 0;
        }
        int completedCount = 0;
        for (SkillNode skillNode : allNodesForSkill) {
            StudentProgress nodeProgress = studentProgressRepository.findByStudentAndSkillNode(student, skillNode).orElse(null);
            if (nodeProgress != null && nodeProgress.getStatus() == RoadmapStepStatus.COMPLETED) {
                completedCount++;
            }
        }
        return (int) Math.round((double) completedCount / allNodesForSkill.size() * 100);
    }

    @Transactional
    public StudentResponse uploadTranscript(MultipartFile file) {
        log.info("Uploading transcript for student...");
        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();
        User user = userRepository.findByUserId(student.getUserId());
        
        // 1. Upload to Supabase
        String url = supabaseStorageService.uploadTranscript(file, user.getUserId().toString());
        student.setTranscriptUrl(url);
        studentRepository.save(student);

        // 2. RAG processing
        try {
            // Delete old documents from vector store for this user to avoid duplicates
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'userId' = ?", user.getUserId().toString());
            
            // Convert PDF to Markdown
            String markdown = pdfToMarkdownService.convertToMarkdown(file);
            
            // Split into chunks
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> documents = splitter.split(List.of(new Document(markdown)));
            
            // Add metadata
            for (Document doc : documents) {
                doc.getMetadata().put("userId", user.getUserId().toString());
                doc.getMetadata().put("source", "transcript");
            }
            
            // Save to VectorStore
            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                log.info("Added {} transcript documents to VectorStore for user {}", documents.size(), user.getUserId());
            }
            
        } catch (Exception e) {
            log.error("Failed to process transcript for RAG", e);
            throw new RuntimeException("Failed to process transcript: " + e.getMessage(), e);
        }

        return studentMapper.toProfileResponse(student, user);
    }
}
