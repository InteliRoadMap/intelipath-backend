package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.ai.client.AiServiceClient;
import com.inteliroadmap.backend.components.CareerCoreSkillDemoter;
import com.inteliroadmap.backend.components.CareerSkillDemandDeriver;
import com.inteliroadmap.backend.components.CareerSkillMarketGrader;
import com.inteliroadmap.backend.components.CoreSkillEligibility;
import com.inteliroadmap.backend.components.RecruitmentCareerClassifier;
import com.inteliroadmap.backend.components.SkillNameCanonicalizer;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.mappers.ScraperMapper;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.RecruitmentSkillRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.services.MarketDemandService;
import com.inteliroadmap.backend.services.SkillExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the {@link SkillExtractionService} interface.
 * Handles the extraction of skills from recruitment descriptions and rebuilds skill trends
 * utilizing an external AI service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillExtractionServiceImpl implements SkillExtractionService {

    private final RecruitmentRepository recruitmentRepository;
    private final SkillRepository skillRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final AiServiceClient aiServiceClient;
    private final CareerSkillMarketGrader careerSkillMarketGrader;
    private final RecruitmentSkillRepository recruitmentSkillRepository;
    private final RecruitmentCareerClassifier recruitmentCareerClassifier;
    private final CareerRoleRepository careerRoleRepository;
    private final CareerSkillDemandDeriver careerSkillDemandDeriver;
    private final SkillNameCanonicalizer skillNameCanonicalizer;
    private final CoreSkillEligibility coreSkillEligibility;
    private final CareerCoreSkillDemoter careerCoreSkillDemoter;

    /**
     * Extracts skills from recruitment descriptions using an external AI service
     * and rebuilds the skill trends data in the database based on the extracted skills.
     * It groups the occurrences of each skill by the application deadline of the recruitment.
     */
    @Transactional
    @Override
    public void extractAndRebuildSkillTrends() {
        log.info("SkillExtractionServiceImpl: Starting Skill Trends extraction process via AI Service...");

        // 1. Postings inside the trend window only.
        //
        // Reading the whole table meant every run re-analysed the entire archive
        // to rebuild rows that nothing outside the window ever reads — a cost
        // that grows with history rather than with the work. MarketDemandService
        // queries exactly this window, so scoping here loses nothing and keeps a
        // 1.000-posting scrape from turning into a 1.000-call LLM job every time.
        LocalDate from = LocalDate.now().minusDays(MarketDemandService.WINDOW_DAYS);
        List<Recruitment> recruitments = recruitmentRepository.findByPostedDateGreaterThanEqual(from);
        if (recruitments.isEmpty()) {
            log.info("SkillExtractionServiceImpl: no postings in the last {} days to process.",
                    MarketDemandService.WINDOW_DAYS);
            return;
        }

        // 2. Send batch request to AI Service
        List<String> descriptions = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();

        for (Recruitment r : recruitments) {
            StringBuilder descBuilder = new StringBuilder();
            // Append title to the description
            String title = com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "title");
            if (title != null) {
                descBuilder.append(title).append(". ");
            }
            // Append all description values
            if (r.getDescriptions() != null) {
                for (Object val : r.getDescriptions().values()) {
                    if (val instanceof java.util.List) {
                        for (Object v : (java.util.List<?>) val) {
                            descBuilder.append(v).append(" ");
                        }
                    } else if (val instanceof java.util.Map) {
                        for (Object v : ((java.util.Map<?, ?>) val).values()) {
                            if (v instanceof java.util.List) {
                                for (Object v2 : (java.util.List<?>) v) {
                                    descBuilder.append(v2).append(" ");
                                }
                            } else {
                                descBuilder.append(v).append(" ");
                            }
                        }
                    } else if (val != null) {
                        descBuilder.append(val).append(" ");
                    }
                }
            }
            
            // Clean up the description string and determine the relevant date.
            // Use the posting date so the trend reflects real demand-over-time;
            // fall back to the deadline, then today, when it's missing.
            String desc = descBuilder.toString().trim();
            LocalDate date = r.getPostedDate() != null ? r.getPostedDate()
                    : (r.getApplicationDeadline() != null ? r.getApplicationDeadline() : LocalDate.now());
            descriptions.add(desc);
            dates.add(date);
        }

        log.info("SkillExtractionServiceImpl: Sending {} descriptions to AI Service", descriptions.size());
        List<List<String>> extractedSkills = aiServiceClient.extractSkills(descriptions);
        if (extractedSkills.isEmpty()) {
            log.warn("SkillExtractionServiceImpl: AI Service returned no skills; nothing to rebuild.");
            return;
        }
        log.info("SkillExtractionServiceImpl: AI Service extraction completed successfully.");

        // 3. Group by (skill, date) -> count.
        //
        // Grouped by the CANONICAL key, not the raw string. The extractor writes whatever
        // the posting called it, so one skill arrives as "Fast API" from one advert and
        // "FastAPI" from the next; grouping on the raw name filed those as two skills and
        // gave each half the evidence. `nameByKey` keeps one spelling per key to look the
        // catalog up with, so the display name still comes from a real posting.
        Map<String, Map<LocalDate, Integer>> skillDateCountMap = new HashMap<>();
        Map<String, String> nameByKey = new HashMap<>();

        for (int i = 0; i < extractedSkills.size(); i++) {
            List<String> skills = extractedSkills.get(i);
            LocalDate date = dates.get(i);

            for (String skillName : skills) {
                String key = skillNameCanonicalizer.matchKey(skillName);
                if (key == null) {
                    continue;
                }
                nameByKey.putIfAbsent(key, skillName);
                Map<LocalDate, Integer> dateCount =
                        skillDateCountMap.computeIfAbsent(key, k -> new HashMap<>());
                dateCount.put(date, dateCount.getOrDefault(date, 0) + 1);
            }
        }

        // 4. Rebuild the window. Only trends inside it are recomputed, so history
        // outside the window survives instead of being wiped by a run that never
        // looked at it.
        skillTrendRepository.deleteByWeekStampGreaterThanEqual(from);
        log.info("SkillExtractionServiceImpl: cleared SkillTrend rows from {} onwards.", from);

        // 5. Save to Database
        List<SkillTrend> newTrends = new ArrayList<>();
        int minted = 0;
        int refused = 0;
        for (Map.Entry<String, Map<LocalDate, Integer>> entry : skillDateCountMap.entrySet()) {
            String skillName = nameByKey.get(entry.getKey());

            // Resolve through the canonicaliser, not a case-insensitive string compare.
            // The old lookup answered "no such skill" for names differing from a real entry
            // by a space or a dot, and every miss inserted a row: that is how "Fast API"
            // came to sit beside "FastAPI" and "React.js" beside "React", each holding half
            // the evidence for one skill.
            Skill skill = skillNameCanonicalizer.resolve(skillName);
            if (skill == null) {
                // A genuinely new technology is worth recording — market demand is exactly
                // where the catalog learns about things the roadmaps do not teach yet. A
                // category word is not: this run minted 126 rows, among them "Software
                // Development" and "API design", none of which anyone can learn.
                if (!coreSkillEligibility.isNameable(skillName)) {
                    refused++;
                    continue;
                }
                skill = skillRepository.save(Skill.builder().skillName(skillName).build());
                skillNameCanonicalizer.remember(skill);
                minted++;
                log.debug("SkillExtractionServiceImpl: '{}' is new to the catalog; added from market data.",
                        skillName);
            }

            for (Map.Entry<LocalDate, Integer> dateEntry : entry.getValue().entrySet()) {
                SkillTrend trend = SkillTrend.builder()
                        .skill(Skill.builder().skillId(skill.getSkillId()).build())
                        .weekStamp(dateEntry.getKey())
                        .jobsNeeded(dateEntry.getValue())
                        .build();
                newTrends.add(trend);
            }
        }

        skillTrendRepository.saveAll(newTrends);
        log.info("SkillExtractionServiceImpl: saved {} SkillTrend record(s) across {} distinct skill(s); "
                        + "{} new to the catalog, {} name(s) refused as too generic to learn.",
                newTrends.size(), skillDateCountMap.size(), minted, refused);

        // 5b. Keep the per-posting detail this method has always had and always thrown
        // away. `extractedSkills` is one list PER POSTING; step 3 collapses it to a
        // market-wide count per skill per day, after which "which skills do Backend
        // postings ask for" can only be answered by paying to read all 913 descriptions
        // again. That is why career_required_skills was hand-written, and why it says
        // Git is required by five careers but not Backend, CI/CD not by DevOps, and
        // Testing not by QA.
        //
        // Isolated: the trends are the expensive part and are already saved, so a
        // failure here must not throw the run away.
        try {
            persistPerPostingSkills(recruitments, extractedSkills, from);
        } catch (Exception e) {
            log.error("SkillExtractionServiceImpl: trends saved, but per-posting skills could not be "
                    + "stored. Per-career demand will be stale until the next run.", e);
        }

        // 6. The catalog's importance grades are derived from exactly the trends
        // just written, so they are recomputed here rather than left to drift
        // until someone runs a migration. Isolated because a failure to re-grade
        // must not throw away a completed extraction — the trends are the
        // expensive part and are already saved.
        try {
            careerSkillMarketGrader.regradeFromMarket();
        } catch (Exception e) {
            log.error("SkillExtractionServiceImpl: trends saved, but re-grading career skills failed. "
                    + "Grades stay as they were until the next run.", e);
        }

        // 6b. HIGH is the readiness denominator and the Skill Map's population, so take out
        // of it whatever a student cannot be measured on. Strictly after the regrade above:
        // that step only touches rows the market named, so it cannot undo this one — the
        // reverse order would re-promote exactly the rows this removes.
        try {
            careerCoreSkillDemoter.demoteUnmeasurableCoreSkills();
        } catch (Exception e) {
            log.error("SkillExtractionServiceImpl: trends saved and re-graded, but demoting "
                    + "unmeasurable core skills failed. Readiness stays on the wider denominator "
                    + "until the next run.", e);
        }

        // 7. A career's catalog gains whatever postings for that career actually asked
        // for. Runs after 5b, which is the only step that knows which posting wanted
        // what — before it existed this table could only be written by hand, and by hand
        // it ended up claiming Git for five careers but not Backend.
        try {
            careerSkillDemandDeriver.deriveFromMarket();
        } catch (Exception e) {
            log.error("SkillExtractionServiceImpl: trends saved, but deriving career skills from "
                    + "measured demand failed. The catalog stays as it was.", e);
        }
    }

    /**
     * Stores which skills each posting asked for, and which career the posting is.
     *
     * <p>Both are recorded per posting rather than aggregated, because the useful
     * questions keep changing shape. Aggregating to a market-wide daily count is what
     * made "what do Backend postings want" cost another full pass through the AI
     * service; a per-posting row makes it a GROUP BY. The career classification is
     * keyword-based and re-derived on every run, so improving the rules never requires
     * paying for extraction again — which is the entire point of this table.
     *
     * @param from the window start the caller used, so the delete cannot reach postings
     *             this run never examined
     */
    private void persistPerPostingSkills(List<Recruitment> recruitments,
                                         List<List<String>> extractedSkills, LocalDate from) {
        int cleared = recruitmentSkillRepository.deleteForPostingsSince(from);

        // Resolving a name to a skill row is the same lookup step 5 already did, so the
        // rows it created (including any newly minted from market data) are all present.
        Map<String, UUID> skillIdByName = new HashMap<>();
        List<RecruitmentSkill> links = new ArrayList<>();
        List<Recruitment> classified = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int withCareer = 0;

        for (int i = 0; i < recruitments.size() && i < extractedSkills.size(); i++) {
            Recruitment recruitment = recruitments.get(i);

            String title = ScraperMapper.str(recruitment.getRecruitmentInfos(), "title");
            UUID careerId = careerIdFor(title);
            if (!Objects.equals(careerId, recruitment.getCareerId())) {
                recruitment.setCareerId(careerId);
                classified.add(recruitment);
            }
            if (careerId != null) {
                withCareer++;
            }

            // A set: a description naming Java six times is one employer asking once.
            // Keyed on the canonical form, so a posting writing both "Fast API" and
            // "FastAPI" counts once rather than twice.
            Set<UUID> seen = new HashSet<>();
            for (String skillName : extractedSkills.get(i)) {
                String nameKey = skillNameCanonicalizer.matchKey(skillName);
                if (nameKey == null) {
                    continue;
                }
                UUID skillId = skillIdByName.computeIfAbsent(nameKey, key -> {
                    Skill skill = skillNameCanonicalizer.resolve(skillName);
                    return skill != null ? skill.getSkillId() : null;
                });
                if (skillId != null && seen.add(skillId)) {
                    links.add(RecruitmentSkill.builder()
                            .recruitmentId(recruitment.getTopCvRecruitmentId())
                            .skillId(skillId)
                            .extractedAt(now)
                            .build());
                }
            }
        }

        recruitmentSkillRepository.saveAll(links);
        if (!classified.isEmpty()) {
            recruitmentRepository.saveAll(classified);
        }
        log.info("SkillExtractionServiceImpl: stored {} posting-skill link(s) across {} posting(s); "
                        + "{} classified into a career, {} left unclassified ({} stale link(s) cleared)",
                links.size(), recruitments.size(), withCareer, recruitments.size() - withCareer, cleared);
    }

    /** The career id for a posting title, or null when the title names no specialisation. */
    private UUID careerIdFor(String title) {
        String careerName = recruitmentCareerClassifier.classify(title);
        if (careerName == null) {
            return null;
        }
        return careerIdByName.computeIfAbsent(careerName, name -> {
            CareerRole career = careerRoleRepository.findByCareerName(name);
            if (career == null) {
                // The classifier names a career the catalog does not have. Worth saying
                // out loud: it means the rules and the career table have drifted apart,
                // and every posting matching that rule is silently uncounted.
                log.warn("SkillExtractionServiceImpl: classifier produced career '{}', which is not in "
                        + "career_roles; those postings will stay unclassified.", name);
                return null;
            }
            return career.getCareerId();
        });
    }

    /** Cache for one run; career rows do not change while an extraction is in flight. */
    private final Map<String, UUID> careerIdByName = new HashMap<>();
}
