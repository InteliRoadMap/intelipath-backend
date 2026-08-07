package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ranks careers by how much their core skill set overlaps the student's.
 *
 * <p><b>Jaccard, per arXiv 2109.02554 §4.1.</b> Treat a career as a set of
 * skills and the student as another, then measure the distance between them:
 *
 * <pre>
 *   J(A, B)        = |A ∩ B| / |A ∪ B|
 *   distance(A, B) = 1 - J(A, B)
 * </pre>
 *
 * <p><b>It suggests; it never decides.</b> Nothing here writes
 * {@code students.career_id}. In the paper more than 99% of career pairs sit
 * between 0.8 and 1.0 and the 0.8 cut-off came from "eye-balling" the
 * distribution — that is not firm enough to pick someone's career for them.
 * What it is firm enough for is putting the most plausible option first and
 * showing the count behind it, so the student can disagree with a number rather
 * than with a black box.
 *
 * <p><b>Why the student's side counts every declared skill</b> while
 * {@link SeniorityCalculator} counts only APPLIED and above: the two answer
 * different questions. Readiness asks "can you do this job", which needs a
 * competence bar. Affinity asks "which job is this person pointed at", and
 * having touched Docker at all is evidence about direction even when it is no
 * evidence about competence.
 *
 * <p>The career's side is {@link SeniorityCalculator#CORE_IMPORTANCE}, the same
 * set the level and readiness figures use — measured, not assumed: widening it
 * to HIGH+AVG left the overlap for the top career unchanged at 9 while the
 * denominator went from 29 to 181, which flattens every career onto the same
 * distance and destroys the ranking.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CareerAffinityCalculator {

    private final StudentSkillRepository studentSkillRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final CareerRoleRepository careerRoleRepository;

    /** Skills named in the reason before it stops being readable. */
    private static final int TOP_SKILLS_NAMED = 3;

    /**
     * @param jaccardDistance 0 = identical sets, 1 = nothing in common
     * @param matched         core skills of this career the student holds
     * @param required        size of the career's core set — the honest denominator
     * @param topMatchingSkills a few of the matches, by name, so the number is checkable
     */
    public record CareerAffinity(UUID careerId,
                                 String careerName,
                                 double jaccardDistance,
                                 int matched,
                                 int required,
                                 List<String> topMatchingSkills) {
    }

    /**
     * Every career, nearest first.
     *
     * <p>Careers with no core skills recorded are dropped rather than ranked at
     * distance 1.0: "we have no data for this role" and "you have nothing in
     * common with this role" are different claims, and only one of them is about
     * the student.
     */
    public List<CareerAffinity> rank(UUID userId) {
        Set<UUID> mine = new HashSet<>();
        for (StudentSkill ss : studentSkillRepository.findByStudent_UserId(userId)) {
            if (ss.getSkill() != null && ss.getSkill().getSkillId() != null) {
                mine.add(ss.getSkill().getSkillId());
            }
        }

        Map<UUID, Set<UUID>> coreByCareer = new HashMap<>();
        Map<UUID, Map<UUID, String>> skillNamesByCareer = new HashMap<>();
        for (CareerRequiredSkill crs : careerRequiredSkillRepository
                .findByImportanceLevelIn(SeniorityCalculator.CORE_IMPORTANCE)) {
            if (crs.getCareerRole() == null || crs.getSkill() == null
                    || crs.getSkill().getSkillId() == null) {
                continue;
            }
            UUID careerId = crs.getCareerRole().getCareerId();
            coreByCareer.computeIfAbsent(careerId, k -> new LinkedHashSet<>())
                    .add(crs.getSkill().getSkillId());
            skillNamesByCareer.computeIfAbsent(careerId, k -> new HashMap<>())
                    .put(crs.getSkill().getSkillId(), crs.getSkill().getSkillName());
        }

        List<CareerAffinity> ranked = new ArrayList<>();
        for (CareerRole career : careerRoleRepository.findAll()) {
            Set<UUID> core = coreByCareer.get(career.getCareerId());
            if (core == null || core.isEmpty()) {
                continue;
            }

            List<String> matchedNames = new ArrayList<>();
            Map<UUID, String> names = skillNamesByCareer.getOrDefault(career.getCareerId(), Map.of());
            int intersection = 0;
            for (UUID skillId : core) {
                if (mine.contains(skillId)) {
                    intersection++;
                    if (matchedNames.size() < TOP_SKILLS_NAMED && names.get(skillId) != null) {
                        matchedNames.add(names.get(skillId));
                    }
                }
            }

            // |A ∪ B| = |A| + |B| - |A ∩ B|. Never zero here: core is non-empty.
            int union = mine.size() + core.size() - intersection;
            double distance = 1.0 - ((double) intersection / union);

            ranked.add(new CareerAffinity(career.getCareerId(), career.getCareerName(),
                    round(distance), intersection, core.size(), List.copyOf(matchedNames)));
        }

        ranked.sort(Comparator
                .comparingDouble(CareerAffinity::jaccardDistance)
                // A student with no skills is equidistant from everything; keep the
                // order stable rather than letting it wobble between requests.
                .thenComparing(CareerAffinity::careerName));
        return List.copyOf(ranked);
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
