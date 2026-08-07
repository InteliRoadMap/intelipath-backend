package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The thresholds are the whole contract here — the SQL that applies them lives in
 * the repository and is exercised against a real database, but which numbers get
 * handed to it, and that only one place decides them, is checkable here.
 */
class CareerSkillMarketGraderTest {

    private final CareerRequiredSkillRepository repository = mock(CareerRequiredSkillRepository.class);
    private final CareerSkillMarketGrader grader = new CareerSkillMarketGrader(repository);

    @Test
    @DisplayName("passes its own thresholds to the query, so the two cannot drift apart")
    void passesItsOwnThresholds() {
        when(repository.regradeByMarketDemand(anyDouble(), anyDouble())).thenReturn(0);

        grader.regradeFromMarket();

        verify(repository).regradeByMarketDemand(
                CareerSkillMarketGrader.HIGH_RATIO, CareerSkillMarketGrader.AVG_RATIO);
    }

    @Test
    @DisplayName("HIGH sits above AVG, and both leave room below for LOW")
    void thresholdsAreOrdered() {
        assertThat(CareerSkillMarketGrader.HIGH_RATIO)
                .isGreaterThan(CareerSkillMarketGrader.AVG_RATIO);
        assertThat(CareerSkillMarketGrader.AVG_RATIO).isGreaterThan(0.0);
        assertThat(CareerSkillMarketGrader.HIGH_RATIO).isLessThan(1.0);
    }

    @Test
    @DisplayName("reports how many rows moved, so a run that changed nothing is visible")
    void reportsChangedCount() {
        when(repository.regradeByMarketDemand(anyDouble(), anyDouble())).thenReturn(125);

        assertThat(grader.regradeFromMarket()).isEqualTo(125);
    }
}
