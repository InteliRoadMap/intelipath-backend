package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.mappers.MarketDemandMapper;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.RecruitmentSkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.services.impl.MarketDemandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDemandServiceImplTest {
    @Mock SkillTrendRepository skillTrendRepository;
    @Mock RecruitmentRepository recruitmentRepository;
    @Mock RecruitmentSkillRepository recruitmentSkillRepository;
    @Mock CareerRequiredSkillRepository careerRequiredSkillRepository;
    @Mock CareerRoleRepository careerRoleRepository;
    @Mock MarketDemandMapper marketDemandMapper;
    @InjectMocks MarketDemandServiceImpl service;

    @Test
    void careerDemandUsesCareerSpecificNumeratorAndDenominator() {
        UUID careerId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        Skill skill = Skill.builder().skillId(skillId).skillName("Java").build();
        CareerRequiredSkill required = CareerRequiredSkill.builder()
                .skill(skill).importanceLevel(ImportanceLevel.HIGH).build();
        SkillDemandResponse expected = SkillDemandResponse.builder().jobCount(70).sampleSize(133).build();

        when(recruitmentRepository.countCareerPostingsSince(eq(careerId), any(LocalDate.class))).thenReturn(133L);
        when(careerRoleRepository.count()).thenReturn(8L);
        when(careerRequiredSkillRepository.findByCareerRole_CareerId(careerId)).thenReturn(List.of(required));
        when(careerRequiredSkillRepository.countCareersPerSkill()).thenReturn(List.<Object[]>of(new Object[]{skillId, 1L}));
        when(recruitmentSkillRepository.demandForCareerSince(eq(careerId), any(LocalDate.class)))
                .thenReturn(List.<Object[]>of(new Object[]{skillId, 70L}));
        when(marketDemandMapper.toSkillDemandResponse(70, 133L, MarketDemandService.WINDOW_DAYS,
                ImportanceLevel.HIGH, 1, 8)).thenReturn(expected);

        Map<UUID, SkillDemandResponse> result = service.demandBySkill(careerId);

        assertSame(expected, result.get(skillId));
        verify(recruitmentRepository, never()).countByPostedDateGreaterThanEqual(any());
        verify(skillTrendRepository, never()).findByWeekStampGreaterThanEqual(any());
    }
}
