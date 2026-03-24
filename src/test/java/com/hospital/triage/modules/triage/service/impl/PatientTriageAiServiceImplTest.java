package com.hospital.triage.modules.triage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.triage.entity.po.TriageAiAudit;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import com.hospital.triage.modules.triage.mapper.TriageAiAuditMapper;
import com.hospital.triage.modules.triage.mapper.TriageRuleMapper;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiRequest;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientTriageAiServiceImplTest {

    @Mock
    private ClinicDeptMapper clinicDeptMapper;
    @Mock
    private TriageRuleMapper triageRuleMapper;
    @Mock
    private TriageAiAuditMapper triageAiAuditMapper;

    private PatientTriageAiProperties properties;
    private PatientTriageAiServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new PatientTriageAiProperties();
        properties.setEnabled(false);
        service = new PatientTriageAiServiceImpl(properties, new ObjectMapper(), clinicDeptMapper, triageRuleMapper, triageAiAuditMapper);
    }

    @Test
    void shouldFallbackWhenAiDisabled() {
        PatientTriageAiRequest request = PatientTriageAiRequest.builder()
                .scene("SELF_QUEUE")
                .visitId(1L)
                .chiefComplaint("chest pain")
                .symptomTags("chest pain")
                .selectedDeptId(1L)
                .currentTriageLevel(4)
                .build();

        PatientTriageAiResult result = service.analyze(request);

        assertThat(result).isNotNull();
        assertThat(result.getSource()).isEqualTo("RULE_FALLBACK");
        assertThat(result.getSuggestedLevel()).isNotNull();
        assertThat(result.getNeedManualReview()).isNotNull();
    }

    @Test
    void shouldUseDatabaseRuleForChiefComplaintWhenAiDisabled() {
        TriageRule rule = new TriageRule();
        rule.setRuleCode("RULE_EMERG_UNCONSCIOUS");
        rule.setRuleName("意识不清急诊优先");
        rule.setSymptomKeyword("意识不清");
        rule.setTriageLevel(1);
        rule.setRecommendDeptId(1L);
        rule.setSpecialWeight(120);
        rule.setFastTrack(1);
        rule.setEnabled(1);
        when(triageRuleMapper.selectList(any())).thenReturn(java.util.List.of(rule));
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "急诊科"));

        PatientTriageAiRequest request = PatientTriageAiRequest.builder()
                .scene("SELF_QUEUE")
                .visitId(2L)
                .chiefComplaint("患者意识不清，伴头晕")
                .symptomTags("头晕")
                .selectedDeptId(4L)
                .currentTriageLevel(4)
                .build();

        PatientTriageAiResult result = service.analyze(request);

        assertThat(result.getSource()).isEqualTo("RULE_FALLBACK");
        assertThat(result.getSuggestedLevel()).isEqualTo(1);
        assertThat(result.getSuggestedDeptId()).isEqualTo(1L);
        assertThat(result.getRiskTags()).contains("RULE_MATCHED");
        assertThat(result.getSuggestedPriorityScore()).isGreaterThanOrEqualTo(1120);
        assertThat(result.getAdvice()).contains("已命中规则");
        assertThat(result.getAdvice()).contains("建议护士人工复核");
        assertThat(result.getNeedManualReview()).isTrue();
    }

    @Test
    void shouldMatchEmergencyLowAcuityRuleWhenAiDisabled() {
        TriageRule emergencyRule = new TriageRule();
        emergencyRule.setId(71L);
        emergencyRule.setRuleCode("RULE_EMERG_MILD_UPPER_RESPIRATORY");
        emergencyRule.setRuleName("鼻塞流涕急诊普通分诊");
        emergencyRule.setSymptomKeyword("鼻塞流涕,咽干鼻塞");
        emergencyRule.setTriageLevel(4);
        emergencyRule.setRecommendDeptId(1L);
        emergencyRule.setSpecialWeight(24);
        emergencyRule.setEnabled(1);

        TriageRule generalRule = new TriageRule();
        generalRule.setId(63L);
        generalRule.setRuleCode("RULE_GEN_COUGH");
        generalRule.setRuleName("咳嗽全科分流");
        generalRule.setSymptomKeyword("咳嗽");
        generalRule.setTriageLevel(4);
        generalRule.setRecommendDeptId(4L);
        generalRule.setSpecialWeight(18);
        generalRule.setEnabled(1);

        when(triageRuleMapper.selectList(any())).thenReturn(java.util.List.of(generalRule, emergencyRule));
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "急诊科"));

        PatientTriageAiRequest request = PatientTriageAiRequest.builder()
                .scene("SELF_QUEUE")
                .visitId(5L)
                .chiefComplaint("鼻塞流涕两天")
                .symptomTags("鼻塞流涕")
                .selectedDeptId(1L)
                .selectedDeptName("急诊科")
                .currentRecommendDeptId(1L)
                .currentRecommendDeptName("急诊科")
                .currentTriageLevel(4)
                .build();

        PatientTriageAiResult result = service.analyze(request);

        assertThat(result.getSource()).isEqualTo("RULE_FALLBACK");
        assertThat(result.getSuggestedLevel()).isEqualTo(4);
        assertThat(result.getSuggestedDeptId()).isEqualTo(1L);
        assertThat(result.getSuggestedDeptName()).isEqualTo("急诊科");
        assertThat(result.getRiskTags()).contains("RULE_MATCHED");
        assertThat(result.getRuleDiff()).isEqualTo("NONE");
    }

    @Test
    void shouldFallbackStablePalpitationToCardiologyWhenNoRuleMatched() {
        when(triageRuleMapper.selectList(any())).thenReturn(java.util.List.of());
        when(clinicDeptMapper.selectList(any())).thenReturn(java.util.List.of(dept(5L, "心内科", "CARDIOLOGY")));
        when(clinicDeptMapper.selectById(5L)).thenReturn(dept(5L, "心内科", "CARDIOLOGY"));

        PatientTriageAiRequest request = PatientTriageAiRequest.builder()
                .scene("SELF_QUEUE")
                .visitId(6L)
                .chiefComplaint("间断心悸三天")
                .symptomTags("间断心悸,活动后胸闷")
                .selectedDeptId(4L)
                .selectedDeptName("全科门诊")
                .currentRecommendDeptId(4L)
                .currentRecommendDeptName("全科门诊")
                .currentTriageLevel(4)
                .build();

        PatientTriageAiResult result = service.analyze(request);

        assertThat(result.getSuggestedDeptId()).isEqualTo(5L);
        assertThat(result.getSuggestedDeptName()).isEqualTo("心内科");
        assertThat(result.getSource()).isEqualTo("RULE_FALLBACK");
        assertThat(result.getRuleDiff()).contains("DEPT_DIFF");
        assertThat(result.getNeedManualReview()).isTrue();
    }

    @Test
    void shouldIgnorePediatricsRuleForAdultWhenAgeIsNotChild() {
        TriageRule rule = new TriageRule();
        rule.setRuleCode("RULE_PED_FEVER");
        rule.setRuleName("Pediatrics fever");
        rule.setSymptomKeyword("发热");
        rule.setTriageLevel(3);
        rule.setRecommendDeptId(2L);
        rule.setEnabled(1);
        when(triageRuleMapper.selectList(any())).thenReturn(java.util.List.of(rule));
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "急诊科"));

        PatientTriageAiRequest request = PatientTriageAiRequest.builder()
                .scene("SELF_QUEUE")
                .visitId(3L)
                .chiefComplaint("发热两天，伴乏力")
                .symptomTags("发热,乏力")
                .age(30)
                .child(true)
                .selectedDeptId(1L)
                .selectedDeptName("急诊科")
                .currentRecommendDeptId(1L)
                .currentRecommendDeptName("急诊科")
                .currentTriageLevel(4)
                .build();

        PatientTriageAiResult result = service.analyze(request);

        assertThat(result.getSuggestedDeptId()).isEqualTo(1L);
        assertThat(result.getSuggestedDeptName()).isEqualTo("急诊科");
        assertThat(result.getRiskTags()).doesNotContain("RULE_MATCHED");
    }

    @Test
    void shouldKeepPediatricsRuleForChildWhenAgeIsBelowThreshold() {
        TriageRule rule = new TriageRule();
        rule.setRuleCode("RULE_PED_FEVER");
        rule.setRuleName("Pediatrics fever");
        rule.setSymptomKeyword("发热");
        rule.setTriageLevel(3);
        rule.setRecommendDeptId(2L);
        rule.setEnabled(1);
        when(triageRuleMapper.selectList(any())).thenReturn(java.util.List.of(rule));
        when(clinicDeptMapper.selectById(2L)).thenReturn(dept(2L, "儿科"));

        PatientTriageAiRequest request = PatientTriageAiRequest.builder()
                .scene("SELF_QUEUE")
                .visitId(4L)
                .chiefComplaint("发热两天，伴咳嗽")
                .symptomTags("发热,咳嗽")
                .age(6)
                .child(false)
                .selectedDeptId(1L)
                .selectedDeptName("急诊科")
                .currentRecommendDeptId(1L)
                .currentRecommendDeptName("急诊科")
                .currentTriageLevel(4)
                .build();

        PatientTriageAiResult result = service.analyze(request);

        assertThat(result.getSuggestedDeptId()).isEqualTo(2L);
        assertThat(result.getSuggestedDeptName()).isEqualTo("儿科");
        assertThat(result.getRiskTags()).contains("RULE_MATCHED");
    }

    @Test
    void shouldSaveAuditRecord() {
        doAnswer(invocation -> {
            TriageAiAudit audit = invocation.getArgument(0);
            audit.setId(123L);
            return 1;
        }).when(triageAiAuditMapper).insert(any(TriageAiAudit.class));

        PatientTriageAiRequest request = PatientTriageAiRequest.builder()
                .scene("TRIAGE_ASSESSMENT")
                .visitId(11L)
                .symptomTags("dyspnea")
                .build();
        PatientTriageAiResult result = PatientTriageAiResult.builder()
                .suggestedLevel(2)
                .suggestedDeptId(1L)
                .riskLevel("HIGH")
                .riskTags(java.util.List.of("RESPIRATORY_DISTRESS"))
                .needManualReview(true)
                .advice("manual review")
                .confidence(0.7D)
                .source("RULE_FALLBACK")
                .provider("local")
                .modelVersion("rule")
                .build();

        Long id = service.saveAudit(11L, 22L, request, result, 2, 800, false);

        assertThat(id).isEqualTo(123L);
    }

    private ClinicDept dept(Long id, String name) {
        return dept(id, name, null);
    }

    private ClinicDept dept(Long id, String name, String code) {
        ClinicDept dept = new ClinicDept();
        dept.setId(id);
        dept.setDeptCode(code);
        dept.setDeptName(name);
        dept.setEnabled(1);
        return dept;
    }
}
