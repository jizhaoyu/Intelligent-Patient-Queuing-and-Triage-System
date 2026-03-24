package com.hospital.triage.modules.queue.service.impl;

import com.hospital.triage.common.enums.QueueSourceTypeEnum;
import com.hospital.triage.common.enums.QueueStatusEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.entity.po.ClinicRoom;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.clinic.mapper.ClinicRoomMapper;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.queue.entity.po.QueueEventLog;
import com.hospital.triage.modules.queue.entity.po.QueueTicket;
import com.hospital.triage.modules.queue.entity.vo.QueueClaimResult;
import com.hospital.triage.modules.queue.entity.vo.QueueRankVO;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;
import com.hospital.triage.modules.queue.mapper.QueueEventLogMapper;
import com.hospital.triage.modules.queue.mapper.QueueTicketMapper;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import com.hospital.triage.modules.visit.service.VisitStatusSnapshotSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueDispatchServiceImplTest {

    @Mock
    private QueueTicketMapper queueTicketMapper;
    @Mock
    private QueueEventLogMapper queueEventLogMapper;
    @Mock
    private ClinicDeptMapper clinicDeptMapper;
    @Mock
    private ClinicRoomMapper clinicRoomMapper;
    @Mock
    private PatientInfoMapper patientInfoMapper;
    @Mock
    private TriageAssessmentMapper triageAssessmentMapper;
    @Mock
    private VisitRecordMapper visitRecordMapper;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    private QueueDispatchServiceImpl service;
    private StubRedisTemplate redisTemplate;
    private DefaultRedisScript<String> callNextRedisScript;
    private AppQueueProperties appQueueProperties;

    @BeforeEach
    void setUp() {
        appQueueProperties = new AppQueueProperties();
        appQueueProperties.setAgingScorePerMinute(2);
        appQueueProperties.setEstimateMinutesPerPerson(5);
        appQueueProperties.setRecallLimit(2);
        appQueueProperties.setCallNextRetryTimes(1);
        appQueueProperties.setAllowDeptFallback(true);
        appQueueProperties.setRoomDiversionHighLevelThreshold(2);
        redisTemplate = new StubRedisTemplate(zSetOperations, hashOperations, valueOperations);
        callNextRedisScript = new DefaultRedisScript<>();
        VisitStatusSnapshotSyncService visitStatusSnapshotSyncService = new VisitStatusSnapshotSyncService(patientInfoMapper);
        service = new QueueDispatchServiceImpl(
                queueTicketMapper,
                queueEventLogMapper,
                clinicDeptMapper,
                clinicRoomMapper,
                patientInfoMapper,
                triageAssessmentMapper,
                visitRecordMapper,
                visitStatusSnapshotSyncService,
                redisTemplate,
                appQueueProperties,
                callNextRedisScript
        );
    }

    @Test
    void shouldOrderSameTriageLevelByPriorityThenAging() {
        QueueTicket highPriority = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 5);
        QueueTicket agingPriority = ticket("T2", QueueStatusEnum.WAITING.name(), 850, 30);
        QueueTicket lowPriority = ticket("T3", QueueStatusEnum.WAITING.name(), 400, 1);
        highPriority.setTriageLevel(4);
        agingPriority.setTriageLevel(4);
        lowPriority.setTriageLevel(4);
        LocalDateTime now = LocalDateTime.now();

        List<QueueTicket> ordered = List.of(highPriority, agingPriority, lowPriority).stream()
                .sorted(Comparator.comparing(ticket -> service.calculateQueueScore(ticket, now)))
                .toList();

        assertThat(ordered).extracting(QueueTicket::getTicketNo).containsExactly("T1", "T2", "T3");
    }

    @Test
    void shouldKeepHigherTriagePatientsAheadOfLowerTriagePatientsEvenWhenLowerTriageWaitedLonger() {
        QueueTicket levelFour = ticket("L4", QueueStatusEnum.WAITING.name(), 980, 120);
        QueueTicket levelThree = ticket("L3", QueueStatusEnum.WAITING.name(), 500, 2);
        QueueTicket levelTwo = ticket("L2", QueueStatusEnum.WAITING.name(), 450, 1);
        levelFour.setTriageLevel(4);
        levelThree.setTriageLevel(3);
        levelTwo.setTriageLevel(2);
        LocalDateTime now = LocalDateTime.now();

        List<QueueTicket> ordered = List.of(levelFour, levelThree, levelTwo).stream()
                .sorted(Comparator.comparing(ticket -> service.calculateQueueScore(ticket, now)))
                .toList();

        assertThat(ordered).extracting(QueueTicket::getTicketNo).containsExactly("L2", "L3", "L4");
    }

    @Test
    void shouldKeepFirstComeFirstServeForSameTriageLevelWhenPriorityScoreEqual() {
        QueueTicket firstArrived = ticket("T1", QueueStatusEnum.WAITING.name(), 700, 0);
        firstArrived.setDeptId(1L);
        firstArrived.setTriageLevel(4);
        firstArrived.setEnqueueTime(LocalDateTime.now().minusSeconds(50));

        QueueTicket laterArrived = ticket("T2", QueueStatusEnum.WAITING.name(), 700, 0);
        laterArrived.setDeptId(1L);
        laterArrived.setTriageLevel(4);
        laterArrived.setEnqueueTime(LocalDateTime.now().minusSeconds(10));

        when(queueTicketMapper.selectOne(any())).thenReturn(laterArrived);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of(laterArrived, firstArrived));

        QueueRankVO result = service.rank("T2");

        assertThat(result.getRank()).isEqualTo(2L);
        assertThat(result.getWaitingCount()).isEqualTo(1L);
    }

    @Test
    void shouldRefreshExistingWaitingTicketWithTriageAutoSource() {
        VisitRecord visitRecord = visit(1L, VisitStatusEnum.TRIAGED.name());
        TriageAssessment assessment = assessment(2L, 1L);
        QueueTicket existing = ticket("T-20260320-0001", QueueStatusEnum.WAITING.name(), 800, 8);
        existing.setVisitId(1L);
        existing.setPatientId(11L);
        existing.setAssessmentId(1L);
        existing.setDeptId(1L);
        existing.setRoomId(2L);
        existing.setSourceType(QueueSourceTypeEnum.KIOSK.name());
        existing.setSourceRemark("院内自助机正式取号");
        existing.setLastAdjustReason(null);

        when(visitRecordMapper.selectById(1L)).thenReturn(visitRecord);
        when(triageAssessmentMapper.selectById(2L)).thenReturn(assessment);
        when(queueTicketMapper.selectOne(any())).thenReturn(existing);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of(existing));
        when(patientInfoMapper.selectById(11L)).thenReturn(patient(11L));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "急诊科")));
        when(clinicRoomMapper.selectBatchIds(any())).thenReturn(List.of(room(2L, "急诊 2 诊室")));

        QueueTicketVO result = service.enqueueAfterTriage(1L, 2L);

        assertThat(existing.getAssessmentId()).isEqualTo(2L);
        assertThat(existing.getSourceType()).isEqualTo(QueueSourceTypeEnum.TRIAGE_AUTO.name());
        assertThat(existing.getSourceRemark()).isEqualTo("分诊自动入队");
        assertThat(result.getSourceType()).isEqualTo(QueueSourceTypeEnum.TRIAGE_AUTO.name());
        assertThat(result.getStatus()).isEqualTo(QueueStatusEnum.WAITING.name());
        verify(visitRecordMapper).updateById(any(VisitRecord.class));
        verify(patientInfoMapper).updateById(any(PatientInfo.class));
    }

    @Test
    void shouldGrantPriorityRevisitPrivilegeForTicketPatient() {
        QueueTicket ticket = ticket("T-20260320-0009", QueueStatusEnum.WAITING.name(), 860, 6);
        ticket.setVisitId(1L);
        ticket.setPatientId(11L);
        ticket.setDeptId(1L);
        ticket.setRoomId(2L);
        ticket.setSourceType(QueueSourceTypeEnum.KIOSK.name());

        PatientInfo originalPatient = patient(11L);
        PatientInfo updatedPatient = patient(11L);
        updatedPatient.setPriorityRevisitPending(true);
        updatedPatient.setPriorityRevisitGrantedBy("doctorA");
        updatedPatient.setPriorityRevisitGrantedTime(LocalDateTime.of(2026, 3, 23, 10, 0));

        when(queueTicketMapper.selectOne(any())).thenReturn(ticket);
        when(patientInfoMapper.selectById(11L)).thenReturn(originalPatient, updatedPatient);

        var result = service.grantPriorityRevisit("T-20260320-0009", "doctorA");

        assertThat(result.getPriorityRevisitPending()).isTrue();
        assertThat(result.getPriorityRevisitGrantedBy()).isEqualTo("doctorA");
        verify(patientInfoMapper).update(any(), any());

        ArgumentCaptor<QueueEventLog> eventCaptor = ArgumentCaptor.forClass(QueueEventLog.class);
        verify(queueEventLogMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("GRANT_PRIORITY_REVISIT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CALLING", "MISSED"})
    void shouldRejectAutoEnqueueWhenExistingTicketAlreadyInProgress(String existingStatus) {
        when(visitRecordMapper.selectById(1L)).thenReturn(visit(1L, VisitStatusEnum.TRIAGED.name()));
        when(triageAssessmentMapper.selectById(2L)).thenReturn(assessment(2L, 1L));
        when(queueTicketMapper.selectOne(any())).thenReturn(ticket("T-20260320-0001", existingStatus, 900, 2));

        assertThatThrownBy(() -> service.enqueueAfterTriage(1L, 2L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("进行中的排队票据");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED"})
    void shouldRejectManualRepairForCompletedOrCancelledVisit(String visitStatus) {
        assertThatThrownBy(() -> invokeValidateVisitStatus(visitStatus))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("异常补录/管理员修复");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGISTERED", "QUEUING", "IN_TREATMENT"})
    void shouldRejectManualRepairWhenVisitNotArrivedOrTriaged(String visitStatus) {
        assertThatThrownBy(() -> invokeValidateVisitStatus(visitStatus))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("异常补录/管理员修复");
    }

    @Test
    void shouldCalculateQueueScoreWithTriageLevelAsPrimaryDimension() throws Exception {
        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("calculateQueueScore", QueueTicket.class, LocalDateTime.class);
        method.setAccessible(true);
        QueueTicket levelThree = ticket("T1", QueueStatusEnum.WAITING.name(), 500, 10);
        QueueTicket levelFour = ticket("T2", QueueStatusEnum.WAITING.name(), 980, 120);
        levelThree.setTriageLevel(3);
        levelFour.setTriageLevel(4);
        LocalDateTime now = LocalDateTime.now();

        double levelThreeScore = (double) method.invoke(service, levelThree, now);
        double levelFourScore = (double) method.invoke(service, levelFour, now);

        assertThat(levelThreeScore).isLessThan(levelFourScore);
    }

    @Test
    void shouldCalculateRoomRankWithinAssignedRoom() {
        QueueTicket current = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 10);
        current.setDeptId(1L);
        current.setRoomId(2L);
        QueueTicket another = ticket("T2", QueueStatusEnum.WAITING.name(), 800, 5);
        another.setDeptId(1L);
        another.setRoomId(2L);

        when(queueTicketMapper.selectOne(any())).thenReturn(current);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of(current, another));

        QueueRankVO result = service.roomRank("T1");

        assertThat(result.getTicketNo()).isEqualTo("T1");
        assertThat(result.getRank()).isEqualTo(1L);
        assertThat(result.getWaitingCount()).isEqualTo(0L);
    }

    @Test
    void shouldCountOnlyTicketsAheadOfCurrentTicket() {
        QueueTicket head = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 10);
        head.setDeptId(1L);
        QueueTicket current = ticket("T2", QueueStatusEnum.WAITING.name(), 800, 6);
        current.setDeptId(1L);
        QueueTicket tail = ticket("T3", QueueStatusEnum.WAITING.name(), 700, 2);
        tail.setDeptId(1L);

        when(queueTicketMapper.selectOne(any())).thenReturn(current);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of(head, current, tail));

        QueueRankVO result = service.rank("T2");

        assertThat(result.getRank()).isEqualTo(2L);
        assertThat(result.getWaitingCount()).isEqualTo(1L);
        assertThat(result.getEstimatedWaitMinutes()).isEqualTo(5L);
    }

    @Test
    void shouldClearStaleCallingTicketWhenDbStatusIsNotCalling() throws Exception {
        QueueTicket waitingTicket = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 10);
        waitingTicket.setDeptId(1L);
        waitingTicket.setRoomId(1L);
        when(valueOperations.get("queue:calling:1")).thenReturn("\"T1\"");
        when(queueTicketMapper.selectOne(any())).thenReturn(waitingTicket, null);

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("clearStaleCallingTicket", Long.class);
        method.setAccessible(true);
        Object result = method.invoke(service, 1L);

        assertThat(redisTemplate.deletedKeys).contains("queue:calling:1");
        assertThat(result).isEqualTo(false);
    }

    @Test
    void shouldRejectCallNextWhenRoomStillHasCallingTicketButCallingKeyExpired() {
        ClinicRoom room = room(1L, "急诊 1 诊室");
        room.setDeptId(1L);
        QueueTicket callingTicket = ticket("T1", QueueStatusEnum.CALLING.name(), 900, 10);
        callingTicket.setDeptId(1L);
        callingTicket.setRoomId(1L);
        callingTicket.setVisitId(1L);

        when(clinicRoomMapper.selectOne(any())).thenReturn(room);
        when(valueOperations.get("queue:calling:1")).thenReturn(null);
        when(queueTicketMapper.selectOne(any())).thenReturn(callingTicket);

        assertThatThrownBy(() -> service.callNext(1L, "doctorA"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前诊室仍有叫号中的患者");

        verify(valueOperations).set("queue:calling:1", "T1", appQueueProperties.getCallingTtlSeconds(), TimeUnit.SECONDS);
    }

    @Test
    void shouldSyncVisitBackToQueuingWhenMarkingTicketMissed() {
        QueueTicket ticket = ticket("T1", QueueStatusEnum.CALLING.name(), 900, 10);
        ticket.setVisitId(1L);
        ticket.setPatientId(11L);
        ticket.setDeptId(1L);
        ticket.setRoomId(2L);

        VisitRecord visitRecord = visit(1L, VisitStatusEnum.IN_TREATMENT.name());
        visitRecord.setCurrentDeptId(1L);
        visitRecord.setCurrentRoomId(2L);

        when(queueTicketMapper.selectOne(any())).thenReturn(ticket);
        when(visitRecordMapper.selectById(1L)).thenReturn(visitRecord);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of());
        when(patientInfoMapper.selectById(11L)).thenReturn(patient(11L));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "急诊科")));
        when(clinicRoomMapper.selectBatchIds(any())).thenReturn(List.of(room(2L, "急诊 2 诊室")));

        QueueTicketVO result = service.markMissed("T1", "doctorA");

        assertThat(result.getStatus()).isEqualTo(QueueStatusEnum.MISSED.name());
        assertThat(visitRecord.getStatus()).isEqualTo(VisitStatusEnum.QUEUING.name());
        verify(visitRecordMapper).updateById(visitRecord);
        verify(patientInfoMapper).updateById(any(PatientInfo.class));
    }

    @Test
    void shouldRecordActualFromStatusWhenRecallingCallingTicket() {
        QueueTicket ticket = ticket("T1", QueueStatusEnum.CALLING.name(), 900, 10);
        ticket.setVisitId(1L);
        ticket.setPatientId(11L);
        ticket.setDeptId(1L);
        ticket.setRoomId(2L);

        VisitRecord visitRecord = visit(1L, VisitStatusEnum.QUEUING.name());
        visitRecord.setCurrentDeptId(1L);
        visitRecord.setCurrentRoomId(2L);

        when(queueTicketMapper.selectOne(any())).thenReturn(ticket);
        when(visitRecordMapper.selectById(1L)).thenReturn(visitRecord);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of());
        when(patientInfoMapper.selectById(11L)).thenReturn(patient(11L));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "急诊科")));
        when(clinicRoomMapper.selectBatchIds(any())).thenReturn(List.of(room(2L, "急诊 2 诊室")));

        QueueTicketVO result = service.recall("T1", "doctorA");

        ArgumentCaptor<QueueEventLog> eventCaptor = ArgumentCaptor.forClass(QueueEventLog.class);
        verify(queueEventLogMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(QueueStatusEnum.CALLING.name());
        assertThat(result.getStatus()).isEqualTo(QueueStatusEnum.CALLING.name());
        assertThat(visitRecord.getStatus()).isEqualTo(VisitStatusEnum.IN_TREATMENT.name());
        verify(valueOperations).set("queue:calling:2", "T1", appQueueProperties.getCallingTtlSeconds(), TimeUnit.SECONDS);
    }

    @Test
    void shouldNormalizeQuotedClaimResultFromRedisScript() throws Exception {
        redisTemplate.executeResult = "\"T1\"";

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("claimNext", Long.class, Long.class);
        method.setAccessible(true);

        QueueClaimResult result = (QueueClaimResult) method.invoke(service, 1L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getTicketNo()).isEqualTo("T1");
    }

    @org.junit.jupiter.api.Disabled("legacy reflection invocation retained for compatibility")
    @Test
    void shouldRefreshWaitingTicketWithRepairSourceMetadata() throws Exception {
        QueueTicket ticket = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 2);
        ticket.setDeptId(1L);
        ticket.setRoomId(2L);
        ticket.setSourceType(QueueSourceTypeEnum.TRIAGE_AUTO.name());
        ticket.setSourceRemark("分诊自动入队");

        TriageAssessment assessment = new TriageAssessment();
        assessment.setId(10L);
        assessment.setTriageLevel(1);
        assessment.setPriorityScore(1000);
        assessment.setFastTrack(1);

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod(
                "refreshWaitingTicket",
                QueueTicket.class,
                TriageAssessment.class,
                Long.class,
                Long.class,
                String.class,
                QueueSourceTypeEnum.class,
                String.class,
                String.class);
        method.setAccessible(true);

        method.invoke(service, ticket, assessment, 1L, 2L, QueueSourceTypeEnum.MANUAL_REPAIR, "管理员手工补录", "异常补录");

        assertThat(ticket.getAssessmentId()).isEqualTo(10L);
        assertThat(ticket.getSourceType()).isEqualTo(QueueSourceTypeEnum.MANUAL_REPAIR.name());
        assertThat(ticket.getSourceRemark()).isEqualTo("管理员手工补录");
        assertThat(ticket.getLastAdjustReason()).isEqualTo("异常补录");
    }

    @Test
    void shouldRefreshWaitingTicketWithRepairSourceMetadataUsingRoomAssignmentStatus() throws Exception {
        QueueTicket ticket = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 2);
        ticket.setDeptId(1L);
        ticket.setRoomId(2L);
        ticket.setSourceType(QueueSourceTypeEnum.TRIAGE_AUTO.name());
        ticket.setSourceRemark("AUTO");

        TriageAssessment assessment = new TriageAssessment();
        assessment.setId(10L);
        assessment.setTriageLevel(1);
        assessment.setPriorityScore(1000);
        assessment.setFastTrack(1);

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod(
                "refreshWaitingTicket",
                QueueTicket.class,
                TriageAssessment.class,
                Long.class,
                Long.class,
                String.class,
                QueueSourceTypeEnum.class,
                String.class,
                String.class);
        method.setAccessible(true);
        method.invoke(service, ticket, assessment, 1L, 2L, "ASSIGNED", QueueSourceTypeEnum.MANUAL_REPAIR, "MANUAL_REPAIR", "REPAIR");

        assertThat(ticket.getAssessmentId()).isEqualTo(10L);
        assertThat(ticket.getSourceType()).isEqualTo(QueueSourceTypeEnum.MANUAL_REPAIR.name());
        assertThat(ticket.getSourceRemark()).isEqualTo("MANUAL_REPAIR");
        assertThat(ticket.getLastAdjustReason()).isEqualTo("REPAIR");
        assertThat(ticket.getRoomAssignmentStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    void shouldEnrichTicketWithoutRoomWhenRoomIdIsNull() {
        QueueTicket ticket = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 2);
        ticket.setPatientId(11L);
        ticket.setDeptId(1L);
        ticket.setRoomId(null);

        when(queueTicketMapper.selectOne(any())).thenReturn(ticket);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of(ticket));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "急诊科")));

        QueueTicketVO result = service.getTicket("T1");

        assertThat(result.getTicketNo()).isEqualTo("T1");
        assertThat(result.getDeptName()).isEqualTo("急诊科");
        assertThat(result.getRoomName()).isNull();
        assertThat(result.getDoctorName()).isNull();
        assertThat(result.getDisplayStatus()).isEqualTo("QUEUEING");
        assertThat(result.getDisplayStatusText()).isEqualTo("排队中");
        assertThat(result.getWaitingForConsultation()).isFalse();
    }

    @Test
    void shouldExposeAiAssessmentSummaryOnTicketDetail() {
        QueueTicket ticket = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 2);
        ticket.setPatientId(11L);
        ticket.setDeptId(1L);
        ticket.setAssessmentId(21L);

        TriageAssessment assessment = assessment(21L, 1L);
        assessment.setSymptomTags("发热两天，伴咽痛");
        assessment.setAiAdvice("建议护士人工复核。");
        assessment.setAiSuggestedLevel(2);
        assessment.setAiRiskLevel("HIGH");
        assessment.setAiNeedManualReview(true);

        when(queueTicketMapper.selectOne(any())).thenReturn(ticket);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of(ticket));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "Dept 1")));
        when(triageAssessmentMapper.selectBatchIds(any())).thenReturn(List.of(assessment));

        QueueTicketVO result = service.getTicket("T1");

        assertThat(result.getChiefComplaint()).isEqualTo("发热两天，伴咽痛");
        assertThat(result.getAiAdvice()).isEqualTo("建议护士人工复核。");
        assertThat(result.getAiSuggestedLevel()).isEqualTo(2);
        assertThat(result.getAiRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getAiNeedManualReview()).isTrue();
        assertThat(result.getAiPriorityAdvice()).contains("AI建议 2级");
        assertThat(result.getAiPriorityAdvice()).contains("风险 高风险");
        assertThat(result.getAiPriorityAdvice()).contains("建议人工复核");
    }

    @Test
    void shouldExposeSurgePriorityExplanationWhenDeptIsCrowded() {
        QueueTicket current = ticket("T1", QueueStatusEnum.WAITING.name(), 980, 18);
        current.setPatientId(11L);
        current.setDeptId(1L);
        current.setTriageLevel(1);
        current.setFastTrack(1);

        QueueTicket ticket2 = ticket("T2", QueueStatusEnum.WAITING.name(), 820, 12);
        ticket2.setDeptId(1L);
        ticket2.setTriageLevel(2);
        QueueTicket ticket3 = ticket("T3", QueueStatusEnum.WAITING.name(), 800, 10);
        ticket3.setDeptId(1L);
        ticket3.setTriageLevel(2);
        QueueTicket ticket4 = ticket("T4", QueueStatusEnum.WAITING.name(), 760, 8);
        ticket4.setDeptId(1L);
        ticket4.setTriageLevel(3);
        QueueTicket ticket5 = ticket("T5", QueueStatusEnum.WAITING.name(), 720, 6);
        ticket5.setDeptId(1L);
        ticket5.setTriageLevel(3);
        QueueTicket ticket6 = ticket("T6", QueueStatusEnum.WAITING.name(), 680, 4);
        ticket6.setDeptId(1L);
        ticket6.setTriageLevel(4);

        when(queueTicketMapper.selectOne(any())).thenReturn(current);
        when(queueTicketMapper.selectList(any())).thenReturn(List.of(current, ticket2, ticket3, ticket4, ticket5, ticket6));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "Dept 1")));

        QueueTicketVO result = service.getTicket("T1");

        assertThat(result.getQueueStrategyMode()).isEqualTo("SURGE");
        assertThat(result.getSurgePriorityApplied()).isTrue();
        assertThat(result.getAgingBoostApplied()).isTrue();
        assertThat(result.getPriorityReason()).contains("高峰策略已加权");
        assertThat(result.getPriorityReason()).contains("老化补偿");
    }

    @Test
    void shouldExposeWaitingForConsultationOnlyForRoomHead() {
        QueueTicket first = ticket("T1", QueueStatusEnum.WAITING.name(), 900, 10);
        first.setPatientId(11L);
        first.setDeptId(1L);
        first.setRoomId(2L);
        first.setConsultationLocked(1);
        QueueTicket second = ticket("T2", QueueStatusEnum.WAITING.name(), 800, 5);
        second.setPatientId(12L);
        second.setDeptId(1L);
        second.setRoomId(2L);

        when(queueTicketMapper.selectList(any())).thenReturn(List.of(first, second));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L), patient(12L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "Dept 1")));
        when(clinicRoomMapper.selectBatchIds(any())).thenReturn(List.of(room(2L, "Room 2")));

        when(queueTicketMapper.selectOne(any())).thenReturn(first);
        QueueTicketVO firstResult = service.getTicket("T1");
        assertThat(firstResult.getDisplayStatus()).isEqualTo("WAITING_FOR_CONSULTATION");
        assertThat(firstResult.getDisplayStatusText()).isEqualTo("候诊中");
        assertThat(firstResult.getWaitingForConsultation()).isTrue();

        when(queueTicketMapper.selectOne(any())).thenReturn(second);
        QueueTicketVO secondResult = service.getTicket("T2");
        assertThat(secondResult.getDisplayStatus()).isEqualTo("QUEUEING");
        assertThat(secondResult.getDisplayStatusText()).isEqualTo("排队中");
        assertThat(secondResult.getWaitingForConsultation()).isFalse();
    }

    @Test
    void shouldPickLeastLoadedRoomForKiosk() throws Exception {
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(room(1L, "急诊1诊室"), room(2L, "急诊2诊室")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-1", 1L, QueueStatusEnum.WAITING.name(), 900, 2),
                        ticketForRoom("R1-2", 1L, QueueStatusEnum.WAITING.name(), 850, 3),
                        ticketForRoom("R2-1", 2L, QueueStatusEnum.WAITING.name(), 800, 2)))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 20),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 800, 25)));

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("pickRoomIdForKiosk", TriageAssessment.class);
        method.setAccessible(true);

        TriageAssessment assessment = assessment(99L, 1L);
        assessment.setTriageLevel(4);
        assessment.setAiSuggestedLevel(4);
        assessment.setAiRiskLevel("LOW");
        assessment.setFastTrack(0);

        Long roomId = (Long) method.invoke(service, assessment);

        assertThat(roomId).isEqualTo(2L);
    }

    @Test
    void shouldPreferPriorityRoomForSevereKioskPatients() throws Exception {
        appQueueProperties.setSeverePriorityRoomByDept(Map.of(1L, 1L));
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(room(1L, "鎬ヨ瘖1璇婂"), room(2L, "鎬ヨ瘖2璇婂")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-W1", 1L, QueueStatusEnum.WAITING.name(), 920, 8),
                        ticketForRoom("R1-C1", 1L, QueueStatusEnum.CALLING.name(), 900, 6),
                        ticketForRoom("R2-W1", 2L, QueueStatusEnum.WAITING.name(), 880, 9),
                        ticketForRoom("R2-W2", 2L, QueueStatusEnum.WAITING.name(), 860, 7)))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 40),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 880, 30)));

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("pickRoomIdForKiosk", TriageAssessment.class);
        method.setAccessible(true);

        TriageAssessment assessment = assessment(100L, 1L);
        assessment.setTriageLevel(1);
        assessment.setAiSuggestedLevel(1);
        assessment.setAiRiskLevel("HIGH");
        assessment.setFastTrack(1);

        Long roomId = (Long) method.invoke(service, assessment);

        assertThat(roomId).isEqualTo(1L);
    }

    @Test
    void shouldBalanceEmergencyRoomsWithoutConfiguredPriorityRoom() throws Exception {
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(room(1L, "Emergency Room 1"), room(2L, "Emergency Room 2")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-W1", 1L, QueueStatusEnum.WAITING.name(), 920, 8),
                        ticketForRoom("R1-C1", 1L, QueueStatusEnum.CALLING.name(), 900, 6),
                        ticketForRoom("R2-W1", 2L, QueueStatusEnum.WAITING.name(), 880, 9),
                        ticketForRoom("R2-W2", 2L, QueueStatusEnum.WAITING.name(), 860, 7)))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 40),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 880, 30)));

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("pickRoomIdForKiosk", TriageAssessment.class);
        method.setAccessible(true);

        TriageAssessment assessment = assessment(100L, 1L);
        assessment.setTriageLevel(1);
        assessment.setAiSuggestedLevel(1);
        assessment.setAiRiskLevel("HIGH");
        assessment.setFastTrack(1);

        Long roomId = (Long) method.invoke(service, assessment);

        assertThat(roomId).isEqualTo(2L);
    }

    @Test
    void shouldDivertHighLevelPatientsToRoomWithoutExistingHighLevelCases() throws Exception {
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(
                room(1L, "急诊1诊室"),
                room(2L, "急诊2诊室"),
                room(3L, "急诊3诊室")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-H1", 1L, QueueStatusEnum.WAITING.name(), 980, 3, 1),
                        ticketForRoom("R2-N1", 2L, QueueStatusEnum.WAITING.name(), 720, 4, 3)))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 40, 1),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 880, 35, 3),
                        ticketForRoom("R3-D1", 3L, QueueStatusEnum.COMPLETED.name(), 860, 30, 4)));

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("pickRoomIdForKiosk", TriageAssessment.class);
        method.setAccessible(true);

        TriageAssessment assessment = assessment(101L, 1L);
        assessment.setTriageLevel(2);
        assessment.setAiSuggestedLevel(2);
        assessment.setAiRiskLevel("HIGH");

        Long roomId = (Long) method.invoke(service, assessment);

        assertThat(roomId).isEqualTo(3L);
    }

    @Test
    void shouldFallbackToLeastLoadedRoomWhenAllRoomsAlreadyHaveHighLevelCases() throws Exception {
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(
                room(1L, "急诊1诊室"),
                room(2L, "急诊2诊室"),
                room(3L, "急诊3诊室")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-H1", 1L, QueueStatusEnum.WAITING.name(), 980, 5, 1),
                        ticketForRoom("R1-H2", 1L, QueueStatusEnum.WAITING.name(), 950, 3, 2),
                        ticketForRoom("R2-H1", 2L, QueueStatusEnum.CALLING.name(), 970, 4, 1),
                        ticketForRoom("R2-H2", 2L, QueueStatusEnum.WAITING.name(), 930, 2, 2),
                        ticketForRoom("R3-H1", 3L, QueueStatusEnum.WAITING.name(), 920, 1, 2)))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 40, 1),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 880, 35, 2),
                        ticketForRoom("R3-D1", 3L, QueueStatusEnum.COMPLETED.name(), 860, 30, 2)));

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("pickRoomIdForKiosk", TriageAssessment.class);
        method.setAccessible(true);

        TriageAssessment assessment = assessment(102L, 1L);
        assessment.setTriageLevel(1);
        assessment.setAiSuggestedLevel(1);
        assessment.setAiRiskLevel("CRITICAL");

        Long roomId = (Long) method.invoke(service, assessment);

        assertThat(roomId).isEqualTo(3L);
    }

    @Test
    void shouldAssignRoomForAutoEnqueueWhenAssessmentRoomIsMissing() {
        VisitRecord visitRecord = visit(1L, VisitStatusEnum.TRIAGED.name());
        TriageAssessment assessment = assessment(2L, 1L);
        assessment.setTriageLevel(2);
        assessment.setAiSuggestedLevel(2);
        assessment.setAiRiskLevel("HIGH");

        when(visitRecordMapper.selectById(1L)).thenReturn(visitRecord);
        when(triageAssessmentMapper.selectById(2L)).thenReturn(assessment);
        when(queueTicketMapper.selectOne(any())).thenReturn(null);
        when(patientInfoMapper.selectById(11L)).thenReturn(patient(11L));
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(
                room(1L, "急诊1诊室"),
                room(2L, "急诊2诊室"),
                room(3L, "急诊3诊室")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-H1", 1L, QueueStatusEnum.WAITING.name(), 980, 3, 1),
                        ticketForRoom("R2-N1", 2L, QueueStatusEnum.WAITING.name(), 720, 4, 3)))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 40, 1),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 880, 35, 3),
                        ticketForRoom("R3-D1", 3L, QueueStatusEnum.COMPLETED.name(), 860, 30, 4)))
                .thenReturn(List.of());
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "急诊科")));
        when(clinicRoomMapper.selectBatchIds(any())).thenReturn(List.of(
                room(1L, "急诊1诊室"),
                room(2L, "急诊2诊室"),
                room(3L, "急诊3诊室")));
        when(triageAssessmentMapper.selectBatchIds(any())).thenReturn(List.of(assessment));

        QueueTicketVO result = service.enqueueAfterTriage(1L, 2L);

        assertThat(result.getRoomId()).isEqualTo(3L);
        verify(queueTicketMapper).insert(any(QueueTicket.class));
        verify(visitRecordMapper).updateById(any(VisitRecord.class));
    }

    @Test
    void shouldPreferEmergencyPriorityRoomBeforeThreshold() throws Exception {
        appQueueProperties.setSeverePriorityRoomByDept(Map.of(1L, 1L));
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "Emergency"));
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(
                room(1L, "Emergency Room 1"),
                room(2L, "Emergency Room 2"),
                room(3L, "Emergency Room 3")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-H1", 1L, QueueStatusEnum.WAITING.name(), 980, 4, 1),
                        ticketForRoom("R2-N1", 2L, QueueStatusEnum.WAITING.name(), 720, 3, 4)))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 20, 1),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 800, 18, 4),
                        ticketForRoom("R3-D1", 3L, QueueStatusEnum.COMPLETED.name(), 780, 16, 4)));

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("pickRoomIdForKiosk", TriageAssessment.class);
        method.setAccessible(true);

        TriageAssessment assessment = assessment(120L, 1L);
        assessment.setTriageLevel(1);
        assessment.setAiSuggestedLevel(1);
        assessment.setAiRiskLevel("CRITICAL");

        Long roomId = (Long) method.invoke(service, assessment);

        assertThat(roomId).isEqualTo(1L);
    }

    @Test
    void shouldDivertEmergencyHighLevelPatientsAwayFromLockedConsultationRooms() throws Exception {
        QueueTicket lockedLowLevel = ticketForRoom("R2-L1", 2L, QueueStatusEnum.WAITING.name(), 650, 2, 4);
        lockedLowLevel.setConsultationLocked(1);

        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "Emergency"));
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(
                room(1L, "Emergency Room 1"),
                room(2L, "Emergency Room 2"),
                room(3L, "Emergency Room 3")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-H1", 1L, QueueStatusEnum.WAITING.name(), 990, 5, 1),
                        ticketForRoom("R1-H2", 1L, QueueStatusEnum.WAITING.name(), 960, 4, 2),
                        lockedLowLevel))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 20, 1),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 800, 18, 4),
                        ticketForRoom("R3-D1", 3L, QueueStatusEnum.COMPLETED.name(), 780, 16, 4)));

        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("pickRoomIdForKiosk", TriageAssessment.class);
        method.setAccessible(true);

        TriageAssessment assessment = assessment(121L, 1L);
        assessment.setTriageLevel(2);
        assessment.setAiSuggestedLevel(2);
        assessment.setAiRiskLevel("HIGH");

        Long roomId = (Long) method.invoke(service, assessment);

        assertThat(roomId).isEqualTo(3L);
    }

    @Test
    void shouldFallbackToEmergencyRoomAssignmentWhenEveryRoomIsUnderPressure() {
        VisitRecord visitRecord = visit(1L, VisitStatusEnum.TRIAGED.name());
        TriageAssessment assessment = assessment(2L, 1L);
        assessment.setTriageLevel(1);
        assessment.setAiSuggestedLevel(1);
        assessment.setAiRiskLevel("CRITICAL");

        QueueTicket lockedLowLevel = ticketForRoom("R3-L1", 3L, QueueStatusEnum.WAITING.name(), 640, 2, 4);
        lockedLowLevel.setConsultationLocked(1);

        when(visitRecordMapper.selectById(1L)).thenReturn(visitRecord);
        when(triageAssessmentMapper.selectById(2L)).thenReturn(assessment);
        when(queueTicketMapper.selectOne(any())).thenReturn(null);
        when(patientInfoMapper.selectById(11L)).thenReturn(patient(11L));
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "Emergency"));
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(
                room(1L, "Emergency Room 1"),
                room(2L, "Emergency Room 2"),
                room(3L, "Emergency Room 3")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-H1", 1L, QueueStatusEnum.WAITING.name(), 990, 5, 1),
                        ticketForRoom("R1-H2", 1L, QueueStatusEnum.WAITING.name(), 970, 4, 2),
                        ticketForRoom("R2-H1", 2L, QueueStatusEnum.CALLING.name(), 980, 3, 1),
                        lockedLowLevel))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 20, 1),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 800, 18, 1),
                        ticketForRoom("R3-D1", 3L, QueueStatusEnum.COMPLETED.name(), 780, 16, 4)))
                .thenReturn(List.of());
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "Emergency")));
        when(triageAssessmentMapper.selectBatchIds(any())).thenReturn(List.of(assessment));

        QueueTicketVO result = service.enqueueAfterTriage(1L, 2L);

        assertThat(result.getRoomId()).isEqualTo(2L);
        assertThat(result.getRoomAssignmentStatus()).isEqualTo("ASSIGNED");
        assertThat(result.getDisplayStatus()).isEqualTo("QUEUEING");
    }

    @Test
    void shouldRepairOverflowTicketWhenLoadingTicketDetail() {
        QueueTicket overflowTicket = ticket("T1", QueueStatusEnum.WAITING.name(), 980, 3);
        overflowTicket.setId(9L);
        overflowTicket.setPatientId(11L);
        overflowTicket.setDeptId(1L);
        overflowTicket.setAssessmentId(21L);
        overflowTicket.setRoomId(null);
        overflowTicket.setTriageLevel(1);
        overflowTicket.setFastTrack(1);
        overflowTicket.setRoomAssignmentStatus("UNASSIGNED_OVERFLOW");

        QueueTicket lockedLowLevel = ticketForRoom("R3-L1", 3L, QueueStatusEnum.WAITING.name(), 640, 2, 4);
        lockedLowLevel.setConsultationLocked(1);

        TriageAssessment assessment = assessment(21L, 1L);
        assessment.setTriageLevel(1);
        assessment.setAiSuggestedLevel(1);
        assessment.setAiRiskLevel("CRITICAL");

        when(queueTicketMapper.selectOne(any())).thenReturn(overflowTicket);
        when(triageAssessmentMapper.selectById(21L)).thenReturn(assessment);
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept(1L, "Emergency"));
        when(clinicRoomMapper.selectList(any())).thenReturn(List.of(
                room(1L, "Emergency Room 1"),
                room(2L, "Emergency Room 2"),
                room(3L, "Emergency Room 3")));
        when(queueTicketMapper.selectList(any()))
                .thenReturn(List.of(
                        ticketForRoom("R1-H1", 1L, QueueStatusEnum.WAITING.name(), 990, 5, 1),
                        ticketForRoom("R1-H2", 1L, QueueStatusEnum.WAITING.name(), 970, 4, 2),
                        ticketForRoom("R2-H1", 2L, QueueStatusEnum.CALLING.name(), 980, 3, 1),
                        lockedLowLevel))
                .thenReturn(List.of(
                        ticketForRoom("R1-D1", 1L, QueueStatusEnum.COMPLETED.name(), 900, 20, 1),
                        ticketForRoom("R2-D1", 2L, QueueStatusEnum.COMPLETED.name(), 800, 18, 1),
                        ticketForRoom("R3-D1", 3L, QueueStatusEnum.COMPLETED.name(), 780, 16, 4)))
                .thenReturn(List.of(overflowTicket))
                .thenReturn(List.of(overflowTicket));
        when(patientInfoMapper.selectBatchIds(any())).thenReturn(List.of(patient(11L)));
        when(clinicDeptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1L, "Emergency")));
        when(clinicRoomMapper.selectBatchIds(any())).thenReturn(List.of(room(2L, "Emergency Room 2")));
        when(triageAssessmentMapper.selectBatchIds(any())).thenReturn(List.of(assessment));

        QueueTicketVO result = service.getTicket("T1");

        assertThat(result.getRoomId()).isEqualTo(2L);
        assertThat(result.getRoomAssignmentStatus()).isEqualTo("ASSIGNED");
        assertThat(result.getRoomName()).isEqualTo("Emergency Room 2");
        verify(queueTicketMapper, atLeastOnce()).updateById(overflowTicket);
    }

    private void invokeValidateVisitStatus(String visitStatus) throws Exception {
        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("validateVisitStatusForManualCreate", VisitRecord.class);
        method.setAccessible(true);
        try {
            method.invoke(service, visit(1L, visitStatus));
        } catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(ex.getTargetException());
        }
    }

    private QueueTicket ticket(String ticketNo, String status, int priorityScore, int waitMinutes) {
        QueueTicket ticket = new QueueTicket();
        ticket.setTicketNo(ticketNo);
        ticket.setPriorityScore(priorityScore);
        ticket.setEnqueueTime(LocalDateTime.now().minusMinutes(waitMinutes));
        ticket.setStatus(status);
        ticket.setConsultationLocked(0);
        ticket.setRoomAssignmentStatus("ASSIGNED");
        return ticket;
    }

    private QueueTicket ticketForRoom(String ticketNo, Long roomId, String status, int priorityScore, int waitMinutes) {
        QueueTicket ticket = ticket(ticketNo, status, priorityScore, waitMinutes);
        ticket.setRoomId(roomId);
        return ticket;
    }

    private QueueTicket ticketForRoom(String ticketNo, Long roomId, String status, int priorityScore, int waitMinutes, Integer triageLevel) {
        QueueTicket ticket = ticketForRoom(ticketNo, roomId, status, priorityScore, waitMinutes);
        ticket.setTriageLevel(triageLevel);
        return ticket;
    }

    private VisitRecord visit(Long id, String status) {
        VisitRecord visitRecord = new VisitRecord();
        visitRecord.setId(id);
        visitRecord.setPatientId(11L);
        visitRecord.setStatus(status);
        return visitRecord;
    }

    private TriageAssessment assessment(Long id, Long visitId) {
        TriageAssessment assessment = new TriageAssessment();
        assessment.setId(id);
        assessment.setVisitId(visitId);
        assessment.setRecommendDeptId(1L);
        assessment.setTriageLevel(2);
        assessment.setPriorityScore(860);
        assessment.setFastTrack(0);
        return assessment;
    }

    private PatientInfo patient(Long id) {
        PatientInfo patientInfo = new PatientInfo();
        patientInfo.setId(id);
        patientInfo.setPatientNo("P0001");
        patientInfo.setPatientName("张三");
        return patientInfo;
    }

    private ClinicDept dept(Long id, String name) {
        ClinicDept dept = new ClinicDept();
        dept.setId(id);
        dept.setDeptCode(id != null && id == 1L ? "EMERGENCY" : "GENERAL");
        dept.setDeptName(name);
        dept.setEnabled(1);
        return dept;
    }

    private ClinicRoom room(Long id, String name) {
        ClinicRoom room = new ClinicRoom();
        room.setId(id);
        room.setRoomName(name);
        room.setDoctorName("张医生");
        return room;
    }

    private static class StubRedisTemplate extends RedisTemplate<String, Object> {

        private final ZSetOperations<String, Object> zSetOperations;
        private final HashOperations<String, Object, Object> hashOperations;
        private final ValueOperations<String, Object> valueOperations;
        private final List<String> deletedKeys = new ArrayList<>();
        private Object executeResult;

        private StubRedisTemplate(ZSetOperations<String, Object> zSetOperations,
                                  HashOperations<String, Object, Object> hashOperations,
                                  ValueOperations<String, Object> valueOperations) {
            this.zSetOperations = zSetOperations;
            this.hashOperations = hashOperations;
            this.valueOperations = valueOperations;
        }

        @Override
        public ZSetOperations<String, Object> opsForZSet() {
            return zSetOperations;
        }

        @Override
        public HashOperations<String, Object, Object> opsForHash() {
            return hashOperations;
        }

        @Override
        public ValueOperations<String, Object> opsForValue() {
            return valueOperations;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script,
                             RedisSerializer<?> argsSerializer,
                             RedisSerializer<T> resultSerializer,
                             List<String> keys,
                             Object... args) {
            return (T) executeResult;
        }

        @Override
        public Boolean delete(String key) {
            deletedKeys.add(key);
            return true;
        }

        @Override
        public Boolean expire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
            return true;
        }
    }
}
