package com.hospital.triage.modules.triage.service.impl;

import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.triage.entity.dto.TriageAssessmentCreateDTO;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import com.hospital.triage.modules.triage.entity.vo.TriageAssessmentVO;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.triage.mapper.TriageRuleMapper;
import com.hospital.triage.modules.triage.service.PatientTriageAiService;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import com.hospital.triage.modules.visit.service.VisitStatusSnapshotSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageAssessmentServiceImplTest {

    @Mock
    private TriageAssessmentMapper triageAssessmentMapper;
    @Mock
    private TriageRuleMapper triageRuleMapper;
    @Mock
    private VisitRecordMapper visitRecordMapper;
    @Mock
    private ClinicDeptMapper clinicDeptMapper;
    @Mock
    private QueueDispatchService queueDispatchService;
    @Mock
    private PatientInfoMapper patientInfoMapper;
    @Mock
    private PatientTriageAiService patientTriageAiService;

    private TriageAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        VisitStatusSnapshotSyncService visitStatusSnapshotSyncService = new VisitStatusSnapshotSyncService(patientInfoMapper);
        service = new TriageAssessmentServiceImpl(
                triageAssessmentMapper,
                triageRuleMapper,
                visitRecordMapper,
                clinicDeptMapper,
                queueDispatchService,
                visitStatusSnapshotSyncService,
                patientTriageAiService
        );
    }

    @Test
    void shouldCreateAssessmentAndMapAutoEnqueueResult() throws Exception {
        TriageAssessmentCreateDTO dto = createDto(10L);
        VisitRecord visitRecord = visit(10L, VisitStatusEnum.ARRIVED.name());
        when(visitRecordMapper.selectById(10L)).thenReturn(visitRecord);
        when(triageRuleMapper.selectList(any())).thenReturn(List.of(rule()));
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "急诊科"));
        when(patientInfoMapper.selectById(11L)).thenReturn(patient());
        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(20L);
            return 1;
        }).when(triageAssessmentMapper).insert(any(TriageAssessment.class));
        when(queueDispatchService.enqueueAfterTriage(10L, 20L)).thenReturn(queueTicket("WAITING", 1L, 2L));

        TriageAssessmentVO result = service.create(dto);

        assertThat(result.getRecommendDeptId()).isEqualTo(1L);
        assertThat(result.getRecommendDeptName()).isEqualTo("急诊科");
        assertThat(result.getQueueCreated()).isTrue();
        assertThat(result.getQueueTicketNo()).isEqualTo("T-20260320-0001");
        assertThat(result.getQueueStatus()).isEqualTo("WAITING");
        assertThat(result.getQueueDeptName()).isEqualTo("急诊科");
        assertThat(result.getQueueRoomName()).isEqualTo("急诊 2 诊室");

        ArgumentCaptor<VisitRecord> visitCaptor = ArgumentCaptor.forClass(VisitRecord.class);
        verify(visitRecordMapper).updateById(visitCaptor.capture());
        assertThat(visitCaptor.getValue().getStatus()).isEqualTo(VisitStatusEnum.TRIAGED.name());
        assertThat(visitCaptor.getValue().getCurrentDeptId()).isEqualTo(1L);
        verify(patientInfoMapper).updateById(any(PatientInfo.class));
    }

    @Test
    void shouldRefreshWaitingQueueWhenReassessingQueuedVisit() {
        TriageAssessmentCreateDTO dto = createDto(null);
        TriageAssessment assessment = new TriageAssessment();
        assessment.setId(20L);
        assessment.setVisitId(10L);
        assessment.setSymptomTags("old");
        when(triageAssessmentMapper.selectById(20L)).thenReturn(assessment);
        when(visitRecordMapper.selectById(10L)).thenReturn(visit(10L, VisitStatusEnum.QUEUING.name()));
        when(triageRuleMapper.selectList(any())).thenReturn(List.of(rule()));
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "急诊科"));
        when(patientInfoMapper.selectById(11L)).thenReturn(patient());
        when(queueDispatchService.enqueueAfterTriage(10L, 20L)).thenReturn(queueTicket("WAITING", 1L, 2L));

        TriageAssessmentVO result = service.reassess(20L, dto);

        assertThat(result.getQueueStatus()).isEqualTo("WAITING");
        assertThat(result.getQueueCreated()).isTrue();
        verify(triageAssessmentMapper, org.mockito.Mockito.atLeastOnce()).updateById(any(TriageAssessment.class));
        verify(queueDispatchService).enqueueAfterTriage(10L, 20L);
    }

    @Test
    void shouldRejectReassessWhenVisitAlreadyCompleted() {
        TriageAssessment assessment = new TriageAssessment();
        assessment.setId(20L);
        assessment.setVisitId(10L);
        when(triageAssessmentMapper.selectById(20L)).thenReturn(assessment);
        when(visitRecordMapper.selectById(10L)).thenReturn(visit(10L, VisitStatusEnum.COMPLETED.name()));

        assertThatThrownBy(() -> service.reassess(20L, createDto(null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可分诊");

        verify(queueDispatchService, never()).enqueueAfterTriage(any(), any());
    }

    @Test
    void shouldRejectReassessWhenVisitAlreadyCancelled() {
        TriageAssessment assessment = new TriageAssessment();
        assessment.setId(20L);
        assessment.setVisitId(10L);
        when(triageAssessmentMapper.selectById(20L)).thenReturn(assessment);
        when(visitRecordMapper.selectById(10L)).thenReturn(visit(10L, VisitStatusEnum.CANCELLED.name()));

        assertThatThrownBy(() -> service.reassess(20L, createDto(null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可分诊");

        verify(queueDispatchService, never()).enqueueAfterTriage(any(), any());
    }

    @Test
    void shouldMatchRuleFromChiefComplaintWhenSymptomTagsMissing() throws Exception {
        TriageRule rule = new TriageRule();
        rule.setEnabled(1);
        rule.setSymptomKeyword("意识不清");
        rule.setTriageLevel(1);
        rule.setRecommendDeptId(1L);
        when(triageRuleMapper.selectList(any())).thenReturn(List.of(rule));

        Method method = TriageAssessmentServiceImpl.class.getDeclaredMethod("matchRule", String.class, String.class);
        method.setAccessible(true);

        TriageRule matchedRule = (TriageRule) method.invoke(service, "患者意识不清，伴头晕", null);

        assertThat(matchedRule).isNotNull();
        assertThat(matchedRule.getRecommendDeptId()).isEqualTo(1L);
        assertThat(matchedRule.getTriageLevel()).isEqualTo(1);
    }

    @Test
    void shouldPreferEmergencyLowAcuityRuleOverGeneralRuleWhenKeywordIsMoreSpecific() throws Exception {
        TriageRule emergencyRule = new TriageRule();
        emergencyRule.setId(70L);
        emergencyRule.setEnabled(1);
        emergencyRule.setRuleCode("RULE_EMERG_MILD_HEADACHE");
        emergencyRule.setSymptomKeyword("轻度头痛,头胀不适");
        emergencyRule.setTriageLevel(4);
        emergencyRule.setRecommendDeptId(1L);
        emergencyRule.setSpecialWeight(22);

        TriageRule generalRule = new TriageRule();
        generalRule.setId(39L);
        generalRule.setEnabled(1);
        generalRule.setRuleCode("RULE_GEN_HEADACHE");
        generalRule.setSymptomKeyword("头痛,头胀");
        generalRule.setTriageLevel(4);
        generalRule.setRecommendDeptId(4L);
        generalRule.setSpecialWeight(20);

        when(triageRuleMapper.selectList(any())).thenReturn(List.of(generalRule, emergencyRule));

        Method matchMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod("matchRule", String.class, String.class);
        matchMethod.setAccessible(true);
        TriageRule matchedRule = (TriageRule) matchMethod.invoke(service, "轻度头痛 4 小时", null);

        Method recommendMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "recommendDeptId", TriageAssessmentCreateDTO.class, TriageRule.class);
        recommendMethod.setAccessible(true);
        Long recommendDeptId = (Long) recommendMethod.invoke(service, new TriageAssessmentCreateDTO(), matchedRule);

        assertThat(matchedRule).isNotNull();
        assertThat(matchedRule.getRuleCode()).isEqualTo("RULE_EMERG_MILD_HEADACHE");
        assertThat(recommendDeptId).isEqualTo(1L);
    }

    @Test
    void shouldPreferCardiologyRuleOverGeneralRuleWhenKeywordIsMoreSpecific() throws Exception {
        TriageRule cardiologyRule = new TriageRule();
        cardiologyRule.setId(78L);
        cardiologyRule.setEnabled(1);
        cardiologyRule.setRuleCode("RULE_CARD_INTERMITTENT_PALPITATION");
        cardiologyRule.setSymptomKeyword("间断心悸,偶发心慌");
        cardiologyRule.setTriageLevel(3);
        cardiologyRule.setRecommendDeptId(5L);
        cardiologyRule.setSpecialWeight(42);

        TriageRule generalRule = new TriageRule();
        generalRule.setId(37L);
        generalRule.setEnabled(1);
        generalRule.setRuleCode("RULE_GEN_PALPITATION");
        generalRule.setSymptomKeyword("心悸,心慌");
        generalRule.setTriageLevel(3);
        generalRule.setRecommendDeptId(4L);
        generalRule.setSpecialWeight(35);

        when(triageRuleMapper.selectList(any())).thenReturn(List.of(generalRule, cardiologyRule));

        Method matchMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod("matchRule", String.class, String.class);
        matchMethod.setAccessible(true);
        TriageRule matchedRule = (TriageRule) matchMethod.invoke(service, "间断心悸三天，活动后明显", null);

        Method recommendMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "recommendDeptId", TriageAssessmentCreateDTO.class, TriageRule.class);
        recommendMethod.setAccessible(true);
        Long recommendDeptId = (Long) recommendMethod.invoke(service, new TriageAssessmentCreateDTO(), matchedRule);

        assertThat(matchedRule).isNotNull();
        assertThat(matchedRule.getRuleCode()).isEqualTo("RULE_CARD_INTERMITTENT_PALPITATION");
        assertThat(recommendDeptId).isEqualTo(5L);
    }

    @Test
    void shouldFailWhenRecommendDeptCannotBeResolved() {
        TriageAssessmentCreateDTO dto = createDto(10L);
        dto.setSymptomTags("");
        when(visitRecordMapper.selectById(10L)).thenReturn(visit(10L, VisitStatusEnum.ARRIVED.name()));
        when(clinicDeptMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("推荐科室");
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

    @Test
    void shouldCopyQueueLocationNamesToViewObject() throws Exception {
        QueueTicketVO queueTicket = new QueueTicketVO();
        queueTicket.setTicketNo("20260320-1-0001");
        queueTicket.setStatus("WAITING");
        queueTicket.setDeptId(1L);
        queueTicket.setRoomId(2L);
        queueTicket.setDeptName("急诊科");
        queueTicket.setRoomName("急诊 2 诊室");

        Method method = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "toVO",
                com.hospital.triage.modules.triage.entity.po.TriageAssessment.class,
                QueueTicketVO.class);
        method.setAccessible(true);

        TriageAssessmentVO vo = (TriageAssessmentVO) method.invoke(service, new TriageAssessment(), queueTicket);

        assertThat(vo.getQueueDeptName()).isEqualTo("急诊科");
        assertThat(vo.getQueueRoomName()).isEqualTo("急诊 2 诊室");
    }

    @Test
    void shouldFallbackStableCoughToRespiratoryWhenNoRuleMatched() throws Exception {
        when(clinicDeptMapper.selectList(any())).thenReturn(List.of(dept(6L, "呼吸内科", "RESPIRATORY")));

        TriageAssessmentCreateDTO dto = new TriageAssessmentCreateDTO();
        dto.setAge(38);
        dto.setSymptomTags("反复咳嗽,晨起咳痰");

        Method recommendMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "recommendDeptId", TriageAssessmentCreateDTO.class, TriageRule.class);
        recommendMethod.setAccessible(true);
        Long recommendDeptId = (Long) recommendMethod.invoke(service, dto, null);

        assertThat(recommendDeptId).isEqualTo(6L);
    }

    @Test
    void shouldIgnorePediatricsRuleForAdultWhenAgeTakesPriority() throws Exception {
        TriageRule rule = pediatricsRule();
        when(clinicDeptMapper.selectList(any())).thenReturn(List.of(dept(4L, "全科门诊", "GENERAL")));

        TriageAssessmentCreateDTO dto = new TriageAssessmentCreateDTO();
        dto.setAge(30);
        dto.setChild(true);
        dto.setSymptomTags("发热,乏力");

        Method sanitizeMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "sanitizeMatchedRule", TriageAssessmentCreateDTO.class, TriageRule.class);
        sanitizeMethod.setAccessible(true);
        TriageRule sanitizedRule = (TriageRule) sanitizeMethod.invoke(service, dto, rule);

        Method recommendMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "recommendDeptId", TriageAssessmentCreateDTO.class, TriageRule.class);
        recommendMethod.setAccessible(true);
        Long recommendDeptId = (Long) recommendMethod.invoke(service, dto, sanitizedRule);

        assertThat(sanitizedRule).isNull();
        assertThat(recommendDeptId).isEqualTo(4L);
    }

    @Test
    void shouldKeepPediatricsRuleForChildWhenAgeIsBelowThreshold() throws Exception {
        TriageRule rule = pediatricsRule();
        TriageAssessmentCreateDTO dto = new TriageAssessmentCreateDTO();
        dto.setAge(6);
        dto.setChild(false);

        Method sanitizeMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "sanitizeMatchedRule", TriageAssessmentCreateDTO.class, TriageRule.class);
        sanitizeMethod.setAccessible(true);
        TriageRule sanitizedRule = (TriageRule) sanitizeMethod.invoke(service, dto, rule);

        Method recommendMethod = TriageAssessmentServiceImpl.class.getDeclaredMethod(
                "recommendDeptId", TriageAssessmentCreateDTO.class, TriageRule.class);
        recommendMethod.setAccessible(true);
        Long recommendDeptId = (Long) recommendMethod.invoke(service, dto, sanitizedRule);

        assertThat(sanitizedRule).isSameAs(rule);
        assertThat(recommendDeptId).isEqualTo(2L);
    }

    private TriageAssessmentCreateDTO createDto(Long visitId) {
        TriageAssessmentCreateDTO dto = new TriageAssessmentCreateDTO();
        dto.setVisitId(visitId);
        dto.setSymptomTags("heart pain");
        dto.setHeartRate(118);
        dto.setBodyTemperature(new BigDecimal("37.1"));
        dto.setBloodOxygen(98);
        dto.setGender("MALE");
        dto.setAge(35);
        return dto;
    }

    private TriageRule rule() {
        TriageRule rule = new TriageRule();
        rule.setEnabled(1);
        rule.setSymptomKeyword("heart");
        rule.setTriageLevel(2);
        rule.setRecommendDeptId(1L);
        return rule;
    }

    private TriageRule pediatricsRule() {
        TriageRule rule = new TriageRule();
        rule.setEnabled(1);
        rule.setRuleCode("RULE_PED_FEVER");
        rule.setRuleName("Pediatrics fever");
        rule.setSymptomKeyword("发热");
        rule.setTriageLevel(3);
        rule.setRecommendDeptId(2L);
        return rule;
    }

    private VisitRecord visit(Long id, String status) {
        VisitRecord visitRecord = new VisitRecord();
        visitRecord.setId(id);
        visitRecord.setPatientId(11L);
        visitRecord.setStatus(status);
        visitRecord.setArrivalTime(LocalDateTime.now().minusMinutes(12));
        return visitRecord;
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

    private QueueTicketVO queueTicket(String status, Long deptId, Long roomId) {
        QueueTicketVO queueTicket = new QueueTicketVO();
        queueTicket.setTicketNo("T-20260320-0001");
        queueTicket.setStatus(status);
        queueTicket.setDeptId(deptId);
        queueTicket.setDeptName("急诊科");
        queueTicket.setRoomId(roomId);
        queueTicket.setRoomName("急诊 2 诊室");
        return queueTicket;
    }

    private PatientInfo patient() {
        PatientInfo patientInfo = new PatientInfo();
        patientInfo.setId(11L);
        patientInfo.setPatientNo("P0000001234");
        patientInfo.setPatientName("张三");
        return patientInfo;
    }
}
