package com.inteliroadmap.backend.ai.tool;

import com.inteliroadmap.backend.domain.entity.FptSubject;
import com.inteliroadmap.backend.domain.entity.FptSubjectSkill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.enums.StudentSubjectStatus;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.FptSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectSkillRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.StudentFptSubjectRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * AI-mentor tool that bridges FPT University subjects and the learning roadmap.
 *
 * Two directions:
 *  - subject → skills → roadmap nodes: "PRO192 teaches Java, which covers the
 *    'Java' node on your Backend roadmap" (used after a transcript analysis to
 *    tell the student which nodes their courses already cover and what to study next).
 *  - skill → subjects: "which FPT course teaches Docker?"
 *
 * When no subject codes are given, the student's own declared PASSED subjects are used.
 */
@Service("fptCurriculumTool")
@Description("""
        Map FPT University subject codes (e.g. PRO192, DBI202, PRJ301) to the skills each subject \
        teaches AND the matching nodes on the student's learning roadmap. Use it (1) after analyzing \
        a transcript to tell which roadmap nodes the passed courses already cover and which nodes to \
        study next, (2) to answer 'what skills does course X give me', or (3) with skillName set, \
        the reverse: 'which FPT course teaches skill Y'. You must provide the student's User ID. \
        Leave subjectCodes empty to use the subjects the student has marked as passed on the platform.""")
public class FptCurriculumTool implements java.util.function.Function<FptCurriculumTool.Request, FptCurriculumTool.Response> {

    private static final int MAX_SUBJECTS = 40;

    private final FptSubjectRepository fptSubjectRepository;
    private final FptSubjectSkillRepository fptSubjectSkillRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentRepository studentRepository;
    private final StudentFptSubjectRepository studentFptSubjectRepository;
    private final CareerRoleRepository careerRoleRepository;

    public FptCurriculumTool(FptSubjectRepository fptSubjectRepository,
                             FptSubjectSkillRepository fptSubjectSkillRepository,
                             SkillNodeRepository skillNodeRepository,
                             StudentRepository studentRepository,
                             StudentFptSubjectRepository studentFptSubjectRepository,
                             CareerRoleRepository careerRoleRepository) {
        this.fptSubjectRepository = fptSubjectRepository;
        this.fptSubjectSkillRepository = fptSubjectSkillRepository;
        this.skillNodeRepository = skillNodeRepository;
        this.studentRepository = studentRepository;
        this.studentFptSubjectRepository = studentFptSubjectRepository;
        this.careerRoleRepository = careerRoleRepository;
    }

    public record Request(UUID userId, List<String> subjectCodes, String skillName) {}

    public record Response(String analysis) {}

    @Override
    public Response apply(Request request) {
        try {
            if (request.userId() == null) {
                return new Response("[TOOL_ERROR] Missing userId — pass the student's User ID from the system prompt.");
            }

            // Scalar query + separate lookup: this tool runs on the AI executor thread
            // where no Hibernate session is open, so navigating Student's LAZY
            // careerRole proxy would throw LazyInitializationException.
            UUID careerId = studentRepository.findCareerIdByUserId(request.userId()).orElse(null);
            String careerName = careerId != null
                    ? careerRoleRepository.findById(careerId).map(c -> c.getCareerName()).orElse(null)
                    : null;

            // Reverse lookup: which subjects teach this skill?
            if (request.skillName() != null && !request.skillName().isBlank()) {
                return new Response(describeSubjectsForSkill(request.skillName().trim(), careerId, careerName));
            }

            List<String> codes = normalizeCodes(request.subjectCodes());
            boolean usedDeclared = false;
            if (codes.isEmpty()) {
                codes = studentFptSubjectRepository.findByUserId(request.userId()).stream()
                        .filter(s -> s.getStatus() == StudentSubjectStatus.PASSED)
                        .map(s -> s.getSubjectCode())
                        .toList();
                usedDeclared = true;
            }
            if (codes.isEmpty()) {
                return new Response("The student has not declared any passed FPT subjects on the platform, "
                        + "and no subject codes were provided. Ask for their courses or a transcript.");
            }
            // "DBI" → DBI202, "PRO" → PRO192/PRO201... so partial codes still work.
            codes = expandPartialCodes(codes);
            if (codes.size() > MAX_SUBJECTS) {
                codes = codes.subList(0, MAX_SUBJECTS);
            }

            StringBuilder out = new StringBuilder();
            if (careerName != null) {
                out.append("Student's target career roadmap: ").append(careerName).append('\n');
            } else {
                out.append("Student has no target career selected — roadmap node mapping unavailable, skills only.\n");
            }
            if (usedDeclared) {
                out.append("Using the ").append(codes.size()).append(" subject(s) the student marked as passed.\n");
            }
            out.append('\n');

            Set<String> coveredNodes = new LinkedHashSet<>();
            for (String code : codes) {
                FptSubject subject = fptSubjectRepository.findById(code).orElse(null);
                List<FptSubjectSkill> links = fptSubjectSkillRepository.findBySubjectCode(code);

                out.append("- ").append(code);
                if (subject != null) {
                    // Subject names are "English_Tiếng Việt"; keep the English half.
                    out.append(" (").append(subject.getName().split("_")[0].trim()).append(')');
                }
                if (links.isEmpty()) {
                    out.append(subject == null
                            ? ": not found in the FPT subject catalog — it may be misspelled or not an FPT code.\n"
                            : ": no technical skill mapping (likely a general-education or soft-skill course).\n");
                    continue;
                }
                List<String> skillParts = new ArrayList<>();
                for (FptSubjectSkill link : links) {
                    List<String> nodeNames = nodesForSkill(link, careerId);
                    coveredNodes.addAll(nodeNames);
                    skillParts.add(nodeNames.isEmpty()
                            ? link.getSkillName()
                            : link.getSkillName() + " -> roadmap node(s): " + String.join(", ", nodeNames));
                }
                out.append(": teaches ").append(String.join("; ", skillParts)).append('\n');
            }

            if (!coveredNodes.isEmpty()) {
                out.append("\nRoadmap nodes these subjects cover: ").append(String.join(", ", coveredNodes));
                out.append("\n(Recommend the student mark these subjects as passed on the platform so the nodes "
                        + "auto-complete, then focus on roadmap nodes NOT in this list.)");
            }
            return new Response(out.toString());
        } catch (Exception e) {
            return new Response("[TOOL_ERROR] " + e.getMessage());
        }
    }

    /** All roadmap nodes for a mapped skill, scoped to the student's career when known. */
    private List<String> nodesForSkill(FptSubjectSkill link, UUID careerId) {
        if (link.getSkillId() == null || careerId == null) {
            return List.of();
        }
        return skillNodeRepository.findBySkill_SkillIdAndCareerRole_CareerId(link.getSkillId(), careerId).stream()
                .map(SkillNode::getNodeName)
                .distinct()
                .toList();
    }

    private String describeSubjectsForSkill(String skillName, UUID careerId, String careerName) {
        List<FptSubjectSkill> links = fptSubjectSkillRepository.findBySkillNameIgnoreCase(skillName);
        if (links.isEmpty()) {
            return "No FPT subject in the catalog maps to the skill '" + skillName
                    + "'. The student would have to learn it through self-study resources on the roadmap.";
        }
        StringBuilder out = new StringBuilder("FPT subjects that teach '" + skillName + "':\n");
        for (FptSubjectSkill link : links) {
            FptSubject subject = fptSubjectRepository.findById(link.getSubjectCode()).orElse(null);
            out.append("- ").append(link.getSubjectCode());
            if (subject != null) {
                out.append(" (").append(subject.getName().split("_")[0].trim()).append(')');
            }
            List<String> nodes = nodesForSkill(link, careerId);
            if (!nodes.isEmpty()) {
                out.append(" — covers node(s) ").append(String.join(", ", nodes));
                if (careerName != null) {
                    out.append(" on the ").append(careerName).append(" roadmap");
                }
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * Resolve partial codes: an exact catalog code passes through; otherwise every
     * catalog code starting with it is substituted ("DBI" → DBI202). Unknown codes
     * are kept so the per-subject loop can report them honestly.
     */
    private List<String> expandPartialCodes(List<String> codes) {
        List<String> out = new ArrayList<>();
        for (String code : codes) {
            if (fptSubjectRepository.existsById(code)) {
                out.add(code);
                continue;
            }
            List<FptSubject> matches = fptSubjectRepository.findByCodeStartingWithOrderByCodeAsc(code);
            if (matches.isEmpty()) {
                out.add(code);
            } else {
                matches.stream().map(FptSubject::getCode).limit(5).forEach(out::add);
            }
        }
        return out.stream().distinct().toList();
    }

    private List<String> normalizeCodes(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase())
                .distinct()
                .toList();
    }
}
