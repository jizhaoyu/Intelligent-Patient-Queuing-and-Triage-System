package com.hospital.triage.modules.patient.service.impl;

import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.patient.entity.dto.PatientSelfQueueEnrollDTO;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueViewVO;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.patient.service.PatientQueueQueryService;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.triage.service.PatientTriageAiService;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiRequest;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiResult;
import com.hospital.triage.modules.visit.entity.dto.VisitCreateDTO;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.entity.vo.VisitVO;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import com.hospital.triage.modules.visit.service.VisitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientSelfQueueServiceImplTest {

    @Mock
    private PatientInfoMapper patientInfoMapper;
    @Mock
    private VisitRecordMapper visitRecordMapper;
    @Mock
    private ClinicDeptMapper clinicDeptMapper;
    @Mock
    private VisitService visitService;
    @Mock
    private TriageAssessmentMapper triageAssessmentMapper;
    @Mock
    private QueueDispatchService queueDispatchService;
    @Mock
    private PatientQueueQueryService patientQueueQueryService;
    @Mock
    private PatientTriageAiService patientTriageAiService;

    private PatientSelfQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PatientSelfQueueServiceImpl(
                patientInfoMapper,
                visitRecordMapper,
                clinicDeptMapper,
                visitService,
                triageAssessmentMapper,
                queueDispatchService,
                patientQueueQueryService,
                patientTriageAiService
        );
    }

    @Test
    void shouldRejectUnknownPatientWhenEnrollingFromKiosk() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        when(patientInfoMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.enroll(enrollDTO()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldRejectAmbiguousPatientNameWhenEnrollingFromKiosk() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        PatientInfo another = existingPatient();
        another.setId(12L);
        another.setPatientNo("P0000005678");
        another.setPhone("13999001234");
        when(patientInfoMapper.selectList(any())).thenReturn(List.of(existingPatient(), another));

        PatientSelfQueueEnrollDTO dto = new PatientSelfQueueEnrollDTO();
        dto.setPatientName("Zhang San");
        dto.setPhoneSuffix("1234");
        dto.setDeptId(1L);

        assertThatThrownBy(() -> service.enroll(dto))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldReturnExistingQueueViewWhenPatientAlreadyQueued() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        when(patientInfoMapper.selectOne(any())).thenReturn(existingPatient());

        PatientQueueViewVO queuedView = new PatientQueueViewVO();
        queuedView.setHasActiveQueue(true);
        queuedView.setQueueStatus("WAITING");
        queuedView.setTicketNo("20260320-1-0001");
        when(patientQueueQueryService.query(any())).thenReturn(queuedView);

        PatientQueueViewVO result = service.enroll(enrollDTO());

        assertThat(result).isSameAs(queuedView);
        verifyNoInteractions(visitService, triageAssessmentMapper, queueDispatchService);
    }

    @Test
    void shouldKeepPriorityRevisitPrivilegeWhenPatientAlreadyQueued() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        PatientInfo patientInfo = existingPatient();
        patientInfo.setPriorityRevisitPending(true);
        when(patientInfoMapper.selectOne(any())).thenReturn(patientInfo);

        PatientQueueViewVO queuedView = new PatientQueueViewVO();
        queuedView.setHasActiveQueue(true);
        queuedView.setQueueStatus("WAITING");
        queuedView.setTicketNo("20260320-1-0001");
        when(patientQueueQueryService.query(any())).thenReturn(queuedView);

        PatientQueueViewVO result = service.enroll(enrollDTO());

        assertThat(result).isSameAs(queuedView);
        verify(patientInfoMapper, never()).update(any(), any());
        verifyNoInteractions(visitService, triageAssessmentMapper, queueDispatchService);
    }

    @Test
    void shouldCreateVisitAndEnqueueFromKioskForKnownPatient() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        when(patientInfoMapper.selectOne(any())).thenReturn(existingPatient());

        PatientQueueViewVO firstQuery = new PatientQueueViewVO();
        firstQuery.setHasActiveQueue(false);
        PatientQueueViewVO finalQuery = new PatientQueueViewVO();
        finalQuery.setHasActiveQueue(true);
        finalQuery.setTicketNo("20260320-1-0001");
        when(patientQueueQueryService.query(any())).thenReturn(firstQuery, finalQuery);

        VisitVO createdVisit = new VisitVO();
        createdVisit.setId(88L);
        VisitVO arrivedVisit = new VisitVO();
        arrivedVisit.setId(88L);
        when(visitService.create(any())).thenReturn(createdVisit);
        when(visitService.arrive(88L)).thenReturn(arrivedVisit);

        VisitRecord registeredVisit = new VisitRecord();
        registeredVisit.setId(88L);
        registeredVisit.setPatientId(11L);
        registeredVisit.setStatus("REGISTERED");
        VisitRecord arrivedRecord = new VisitRecord();
        arrivedRecord.setId(88L);
        arrivedRecord.setPatientId(11L);
        arrivedRecord.setStatus("ARRIVED");
        when(visitRecordMapper.selectById(88L)).thenReturn(registeredVisit, arrivedRecord);

        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(66L);
            return 1;
        }).when(triageAssessmentMapper).insert(any(TriageAssessment.class));

        PatientQueueViewVO result = service.enroll(enrollDTO());

        assertThat(result).isSameAs(finalQuery);
        verify(queueDispatchService).enqueueFromKiosk(88L, 66L);

        ArgumentCaptor<TriageAssessment> assessmentCaptor = ArgumentCaptor.forClass(TriageAssessment.class);
        verify(triageAssessmentMapper).insert(assessmentCaptor.capture());
        TriageAssessment assessment = assessmentCaptor.getValue();
        assertThat(assessment.getVisitId()).isEqualTo(88L);
        assertThat(assessment.getRecommendDeptId()).isEqualTo(1L);
        assertThat(assessment.getAssessor()).isEqualTo("kiosk");
        assertThat(assessment.getGender()).isEqualTo("MALE");
        assertThat(assessment.getAge()).isEqualTo(LocalDate.now().getYear() - 1990);
        verify(queueDispatchService, never()).enqueueAfterTriage(any(), any());
    }

    @Test
    void shouldCreateVisitAndEnqueueFromKioskForUniquePatientName() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        when(patientInfoMapper.selectList(any())).thenReturn(List.of(existingPatient()));

        PatientQueueViewVO firstQuery = new PatientQueueViewVO();
        firstQuery.setHasActiveQueue(false);
        PatientQueueViewVO finalQuery = new PatientQueueViewVO();
        finalQuery.setHasActiveQueue(true);
        finalQuery.setTicketNo("20260320-1-0001");
        when(patientQueueQueryService.query(any())).thenReturn(firstQuery, finalQuery);

        VisitVO createdVisit = new VisitVO();
        createdVisit.setId(88L);
        VisitVO arrivedVisit = new VisitVO();
        arrivedVisit.setId(88L);
        when(visitService.create(any())).thenReturn(createdVisit);
        when(visitService.arrive(88L)).thenReturn(arrivedVisit);

        VisitRecord registeredVisit = new VisitRecord();
        registeredVisit.setId(88L);
        registeredVisit.setPatientId(11L);
        registeredVisit.setStatus("REGISTERED");
        VisitRecord arrivedRecord = new VisitRecord();
        arrivedRecord.setId(88L);
        arrivedRecord.setPatientId(11L);
        arrivedRecord.setStatus("ARRIVED");
        when(visitRecordMapper.selectById(88L)).thenReturn(registeredVisit, arrivedRecord);

        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(66L);
            return 1;
        }).when(triageAssessmentMapper).insert(any(TriageAssessment.class));

        PatientSelfQueueEnrollDTO dto = new PatientSelfQueueEnrollDTO();
        dto.setPatientName("Zhang San");
        dto.setPhoneSuffix("1234");
        dto.setDeptId(1L);
        dto.setChiefComplaint("Chest pain");

        PatientQueueViewVO result = service.enroll(dto);

        assertThat(result).isSameAs(finalQuery);
        verify(queueDispatchService).enqueueFromKiosk(88L, 66L);
    }

    @Test
    void shouldConsumePriorityRevisitPrivilegeOnNextSuccessfulEnroll() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        PatientInfo patientInfo = existingPatient();
        patientInfo.setPriorityRevisitPending(true);
        when(patientInfoMapper.selectOne(any())).thenReturn(patientInfo);

        PatientQueueViewVO firstQuery = new PatientQueueViewVO();
        firstQuery.setHasActiveQueue(false);
        PatientQueueViewVO finalQuery = new PatientQueueViewVO();
        finalQuery.setHasActiveQueue(true);
        finalQuery.setTicketNo("20260320-1-0003");
        when(patientQueueQueryService.query(any())).thenReturn(firstQuery, finalQuery);

        VisitVO createdVisit = new VisitVO();
        createdVisit.setId(101L);
        VisitVO arrivedVisit = new VisitVO();
        arrivedVisit.setId(101L);
        when(visitService.create(any())).thenReturn(createdVisit);
        when(visitService.arrive(101L)).thenReturn(arrivedVisit);

        VisitRecord registeredVisit = new VisitRecord();
        registeredVisit.setId(101L);
        registeredVisit.setPatientId(11L);
        registeredVisit.setStatus("REGISTERED");
        VisitRecord arrivedRecord = new VisitRecord();
        arrivedRecord.setId(101L);
        arrivedRecord.setPatientId(11L);
        arrivedRecord.setStatus("ARRIVED");
        when(visitRecordMapper.selectById(101L)).thenReturn(registeredVisit, arrivedRecord);

        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(78L);
            return 1;
        }).when(triageAssessmentMapper).insert(any(TriageAssessment.class));

        PatientQueueViewVO result = service.enroll(enrollDTO());

        assertThat(result).isSameAs(finalQuery);
        verify(queueDispatchService).enqueueFromKiosk(101L, 78L);
        verify(patientInfoMapper).update(any(), any());

        ArgumentCaptor<PatientTriageAiRequest> aiRequestCaptor = ArgumentCaptor.forClass(PatientTriageAiRequest.class);
        verify(patientTriageAiService).analyze(aiRequestCaptor.capture());
        assertThat(aiRequestCaptor.getValue().getRevisit()).isTrue();

        ArgumentCaptor<TriageAssessment> assessmentCaptor = ArgumentCaptor.forClass(TriageAssessment.class);
        verify(triageAssessmentMapper).insert(assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue().getRevisit()).isTrue();
        assertThat(assessmentCaptor.getValue().getManualAdjustScore()).isEqualTo(120);
        assertThat(assessmentCaptor.getValue().getPriorityScore()).isEqualTo(120);
    }

    @Test
    void shouldNotUsePriorityRevisitToOvertakeLevelOnePatients() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        PatientInfo patientInfo = existingPatient();
        patientInfo.setPriorityRevisitPending(true);
        when(patientInfoMapper.selectOne(any())).thenReturn(patientInfo);
        when(patientTriageAiService.analyze(any())).thenReturn(PatientTriageAiResult.builder()
                .suggestedLevel(2)
                .suggestedDeptId(1L)
                .suggestedPriorityScore(950)
                .build());

        PatientQueueViewVO firstQuery = new PatientQueueViewVO();
        firstQuery.setHasActiveQueue(false);
        PatientQueueViewVO finalQuery = new PatientQueueViewVO();
        finalQuery.setHasActiveQueue(true);
        finalQuery.setTicketNo("20260320-1-0004");
        when(patientQueueQueryService.query(any())).thenReturn(firstQuery, finalQuery);

        VisitVO createdVisit = new VisitVO();
        createdVisit.setId(102L);
        VisitVO arrivedVisit = new VisitVO();
        arrivedVisit.setId(102L);
        when(visitService.create(any())).thenReturn(createdVisit);
        when(visitService.arrive(102L)).thenReturn(arrivedVisit);

        VisitRecord registeredVisit = new VisitRecord();
        registeredVisit.setId(102L);
        registeredVisit.setPatientId(11L);
        registeredVisit.setStatus("REGISTERED");
        VisitRecord arrivedRecord = new VisitRecord();
        arrivedRecord.setId(102L);
        arrivedRecord.setPatientId(11L);
        arrivedRecord.setStatus("ARRIVED");
        when(visitRecordMapper.selectById(102L)).thenReturn(registeredVisit, arrivedRecord);

        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(79L);
            return 1;
        }).when(triageAssessmentMapper).insert(any(TriageAssessment.class));

        service.enroll(enrollDTO());

        ArgumentCaptor<TriageAssessment> assessmentCaptor = ArgumentCaptor.forClass(TriageAssessment.class);
        verify(triageAssessmentMapper).insert(assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue().getTriageLevel()).isEqualTo(2);
        assertThat(assessmentCaptor.getValue().getManualAdjustScore()).isEqualTo(49);
        assertThat(assessmentCaptor.getValue().getPriorityScore()).isEqualTo(999);
    }

    @Test
    void shouldCreatePatientRecordAndEnqueueForNewPatient() {
        when(clinicDeptMapper.selectById(1L)).thenReturn(enabledDept(1L, "Emergency"));
        when(patientInfoMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            PatientInfo patientInfo = invocation.getArgument(0);
            patientInfo.setId(21L);
            return 1;
        }).when(patientInfoMapper).insert(any(PatientInfo.class));

        PatientQueueViewVO firstQuery = new PatientQueueViewVO();
        firstQuery.setHasActiveQueue(false);
        PatientQueueViewVO finalQuery = new PatientQueueViewVO();
        finalQuery.setHasActiveQueue(true);
        finalQuery.setTicketNo("20260320-1-0002");
        when(patientQueueQueryService.query(any())).thenReturn(firstQuery, finalQuery);

        VisitVO createdVisit = new VisitVO();
        createdVisit.setId(99L);
        VisitVO arrivedVisit = new VisitVO();
        arrivedVisit.setId(99L);
        when(visitService.create(any())).thenReturn(createdVisit);
        when(visitService.arrive(99L)).thenReturn(arrivedVisit);

        VisitRecord registeredVisit = new VisitRecord();
        registeredVisit.setId(99L);
        registeredVisit.setPatientId(21L);
        registeredVisit.setStatus("REGISTERED");
        VisitRecord arrivedRecord = new VisitRecord();
        arrivedRecord.setId(99L);
        arrivedRecord.setPatientId(21L);
        arrivedRecord.setStatus("ARRIVED");
        when(visitRecordMapper.selectById(99L)).thenReturn(registeredVisit, arrivedRecord);

        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(77L);
            return 1;
        }).when(triageAssessmentMapper).insert(any(TriageAssessment.class));

        PatientSelfQueueEnrollDTO dto = new PatientSelfQueueEnrollDTO();
        dto.setPatientMode("NEW");
        dto.setPatientName("Li Si");
        dto.setPhone("13800004567");
        dto.setGender("MALE");
        dto.setBirthDate(LocalDate.of(1995, 5, 20));
        dto.setAllergyHistory("penicillin");
        dto.setSpecialTags("wheelchair");
        dto.setDeptId(1L);
        dto.setChiefComplaint("Fever");

        PatientQueueViewVO result = service.enroll(dto);

        assertThat(result).isSameAs(finalQuery);
        verify(queueDispatchService).enqueueFromKiosk(99L, 77L);

        ArgumentCaptor<PatientInfo> patientCaptor = ArgumentCaptor.forClass(PatientInfo.class);
        verify(patientInfoMapper).insert(patientCaptor.capture());
        PatientInfo createdPatient = patientCaptor.getValue();
        assertThat(createdPatient.getId()).isEqualTo(21L);
        assertThat(createdPatient.getPatientName()).isEqualTo("Li Si");
        assertThat(createdPatient.getPhone()).isEqualTo("13800004567");
        assertThat(createdPatient.getGender()).isEqualTo("MALE");
        assertThat(createdPatient.getBirthDate()).isEqualTo(LocalDate.of(1995, 5, 20));
        assertThat(createdPatient.getAllergyHistory()).isEqualTo("penicillin");
        assertThat(createdPatient.getSpecialTags()).isEqualTo("wheelchair");
        assertThat(createdPatient.getPatientNo()).startsWith("P");

        ArgumentCaptor<VisitCreateDTO> visitCaptor = ArgumentCaptor.forClass(VisitCreateDTO.class);
        verify(visitService).create(visitCaptor.capture());
        assertThat(visitCaptor.getValue().getPatientId()).isEqualTo(21L);
        assertThat(visitCaptor.getValue().getChiefComplaint()).isEqualTo("Fever");
    }

    private PatientSelfQueueEnrollDTO enrollDTO() {
        PatientSelfQueueEnrollDTO dto = new PatientSelfQueueEnrollDTO();
        dto.setPatientNo("P0000001234");
        dto.setPhoneSuffix("1234");
        dto.setDeptId(1L);
        dto.setChiefComplaint("Chest pain");
        return dto;
    }

    private PatientInfo existingPatient() {
        PatientInfo patientInfo = new PatientInfo();
        patientInfo.setId(11L);
        patientInfo.setPatientNo("P0000001234");
        patientInfo.setPatientName("Zhang San");
        patientInfo.setPhone("13800001234");
        patientInfo.setGender("MALE");
        patientInfo.setBirthDate(LocalDate.of(1990, 1, 1));
        return patientInfo;
    }

    private ClinicDept enabledDept(Long id, String name) {
        ClinicDept dept = new ClinicDept();
        dept.setId(id);
        dept.setDeptName(name);
        dept.setEnabled(1);
        return dept;
    }
}
