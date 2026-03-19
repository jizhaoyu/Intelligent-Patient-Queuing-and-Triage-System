package com.hospital.triage.modules.triage.service.impl;

import com.hospital.triage.modules.triage.entity.dto.TriageAssessmentCreateDTO;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TriageAssessmentServiceImplTest {

    private TriageAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TriageAssessmentServiceImpl(null, null, null, null);
    }

    @Test
    void shouldCalculatePriorityScoreWithLevelSpecialAgingAndManualAdjust() throws Exception {
        TriageAssessmentCreateDTO dto = new TriageAssessmentCreateDTO();
        dto.setPregnant(true);
        dto.setManualAdjustScore(30);
        TriageRule rule = new TriageRule();
        rule.setSpecialWeight(20);
        Method method = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "calculatePriorityScore", TriageAssessmentCreateDTO.class, Integer.class, LocalDateTime.class, TriageRule.class);
        method.setAccessible(true);

        int score = (int) method.invoke(service, dto, 2, LocalDateTime.now().minusMinutes(15), rule);

        assertThat(score).isEqualTo(700 + 100 + 30 + 30);
    }

    @Test
    void shouldEscalateToLevelOneWhenBloodOxygenTooLow() throws Exception {
        TriageAssessmentCreateDTO dto = new TriageAssessmentCreateDTO();
        dto.setBloodOxygen(85);
        dto.setBodyTemperature(new BigDecimal("37.2"));
        Method method = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "determineLevel", TriageAssessmentCreateDTO.class, com.hospital.triage.modules.triage.entity.po.TriageRule.class);
        method.setAccessible(true);

        int level = (int) method.invoke(service, dto, null);

        assertThat(level).isEqualTo(1);
    }
}
