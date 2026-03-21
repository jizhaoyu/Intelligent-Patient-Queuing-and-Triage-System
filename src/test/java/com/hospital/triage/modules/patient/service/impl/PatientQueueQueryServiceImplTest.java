package com.hospital.triage.modules.patient.service.impl;

import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.entity.po.ClinicRoom;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.clinic.mapper.ClinicRoomMapper;
import com.hospital.triage.modules.patient.entity.dto.PatientQueueQueryDTO;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueViewVO;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.queue.entity.vo.QueueRankVO;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientQueueQueryServiceImplTest {

    @Mock
    private PatientInfoMapper patientInfoMapper;
    @Mock
    private VisitRecordMapper visitRecordMapper;
    @Mock
    private QueueDispatchService queueDispatchService;
    @Mock
    private ClinicDeptMapper clinicDeptMapper;
    @Mock
    private ClinicRoomMapper clinicRoomMapper;
    @Mock
    private TriageAssessmentMapper triageAssessmentMapper;

    private PatientQueueQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PatientQueueQueryServiceImpl(
                patientInfoMapper,
                visitRecordMapper,
                queueDispatchService,
                clinicDeptMapper,
                clinicRoomMapper,
                triageAssessmentMapper
        );
    }

    @ParameterizedTest
    @MethodSource("queueStatusCases")
    void shouldEchoQueueStateConsistently(String status,
                                          boolean hasActiveQueue,
                                          String expectedStatusText,
                                          String expectedMessage) {
        mockBaseContext();
        QueueTicketVO ticket = queueTicket(status);
        when(queueDispatchService.getLatestTicketByVisitId(11L)).thenReturn(ticket);

        PatientQueueViewVO result = service.query(queryDTO());

        assertThat(result.getQueueStatus()).isEqualTo(status);
        assertThat(result.isHasActiveQueue()).isEqualTo(hasActiveQueue);
        assertThat(result.getQueueStatusText()).isEqualTo(expectedStatusText);
        assertThat(result.getQueueMessage()).isEqualTo(expectedMessage);
        assertThat(result.getDeptName()).isEqualTo("急诊科");
        assertThat(result.getRoomName()).isEqualTo("急诊 2 诊室");
        verify(queueDispatchService, never()).rank(any());
        verify(queueDispatchService, never()).roomRank(any());
    }

    @Test
    void shouldKeepWaitingPatientQueuedWhenNotFirstInRoom() {
        mockBaseContext();
        when(queueDispatchService.getLatestTicketByVisitId(11L)).thenReturn(queueTicket("WAITING"));
        when(queueDispatchService.rank("T-20260320-0001")).thenReturn(rank(2L, 4L, 20L));
        when(queueDispatchService.roomRank("T-20260320-0001")).thenReturn(rank(2L, 1L, 5L));

        PatientQueueViewVO result = service.query(queryDTO());

        assertThat(result.getQueueStatus()).isEqualTo("WAITING");
        assertThat(result.isHasActiveQueue()).isTrue();
        assertThat(result.getQueueStatusText()).isEqualTo("排队中");
        assertThat(result.getQueueMessage()).isEqualTo("您已完成取号并进入排队，请在候诊区耐心等待，留意现场叫号信息");
        assertThat(result.getRank()).isEqualTo(2L);
        assertThat(result.getWaitingCount()).isEqualTo(4L);
        assertThat(result.getEstimatedWaitMinutes()).isEqualTo(20L);
    }

    @Test
    void shouldMarkFirstWaitingPatientInRoomAsReadyForConsultation() {
        mockBaseContext();
        when(queueDispatchService.getLatestTicketByVisitId(11L)).thenReturn(queueTicket("WAITING"));
        when(queueDispatchService.rank("T-20260320-0001")).thenReturn(rank(2L, 4L, 20L));
        when(queueDispatchService.roomRank("T-20260320-0001")).thenReturn(rank(1L, 0L, 0L));

        PatientQueueViewVO result = service.query(queryDTO());

        assertThat(result.getQueueStatus()).isEqualTo("WAITING");
        assertThat(result.isHasActiveQueue()).isTrue();
        assertThat(result.getQueueStatusText()).isEqualTo("候诊中");
        assertThat(result.getQueueMessage()).isEqualTo("即将轮到您，请在当前诊室门口候诊，留意现场叫号与屏幕提示");
        assertThat(result.getRank()).isEqualTo(2L);
        assertThat(result.getWaitingCount()).isEqualTo(4L);
        assertThat(result.getEstimatedWaitMinutes()).isEqualTo(20L);
    }

    @Test
    void shouldReturnNoneWhenVisitExistsButTicketNotGeneratedYet() {
        mockBaseContext();
        when(queueDispatchService.getLatestTicketByVisitId(11L)).thenReturn(null);

        PatientQueueViewVO result = service.query(queryDTO());

        assertThat(result.isHasActiveQueue()).isFalse();
        assertThat(result.getQueueStatus()).isEqualTo("NONE");
        assertThat(result.getQueueStatusText()).isEqualTo("暂无排队票据");
        assertThat(result.getQueueMessage()).isEqualTo("您已完成分诊，当前暂未生成排队票据，请留意护士台通知");
        assertThat(result.getVisitStatus()).isEqualTo("TRIAGED");
    }

    @Test
    void shouldSupportPatientNameCredentialWhenMatchedUniquely() {
        when(patientInfoMapper.selectList(any())).thenReturn(List.of(patient()));
        when(visitRecordMapper.selectById(11L)).thenReturn(visit());
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept());
        when(clinicRoomMapper.selectById(2L)).thenReturn(room());
        when(queueDispatchService.getLatestTicketByVisitId(11L)).thenReturn(queueTicket("WAITING"));
        when(queueDispatchService.rank("T-20260320-0001")).thenReturn(rank(2L, 4L, 20L));
        when(queueDispatchService.roomRank("T-20260320-0001")).thenReturn(rank(2L, 1L, 5L));

        PatientQueueQueryDTO dto = new PatientQueueQueryDTO();
        dto.setPatientName("张三");
        dto.setPhoneSuffix("1234");

        PatientQueueViewVO result = service.query(dto);

        assertThat(result.getPatientNo()).isEqualTo("P0000001234");
        assertThat(result.getPatientName()).isEqualTo("张*");
    }

    @Test
    void shouldRejectAmbiguousPatientNameCredential() {
        PatientInfo another = patient();
        another.setId(8L);
        another.setPatientNo("P0000005678");
        another.setPhone("13999001234");
        when(patientInfoMapper.selectList(any())).thenReturn(List.of(patient(), another));

        PatientQueueQueryDTO dto = new PatientQueueQueryDTO();
        dto.setPatientName("张三");
        dto.setPhoneSuffix("1234");

        assertThatThrownBy(() -> service.query(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessage("患者信息或校验信息不正确");
    }

    @Test
    void shouldReturnGenericErrorWhenPatientCredentialDoesNotMatch() {
        PatientInfo patientInfo = patient();
        patientInfo.setPhone("13800005678");
        when(patientInfoMapper.selectOne(any())).thenReturn(patientInfo);

        assertThatThrownBy(() -> service.query(queryDTO()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("患者信息或校验信息不正确");
    }

    @Test
    void shouldReturnGenericErrorWhenPatientDoesNotExist() {
        when(patientInfoMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.query(queryDTO()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("患者信息或校验信息不正确");
    }

    private void mockBaseContext() {
        when(patientInfoMapper.selectOne(any())).thenReturn(patient());
        when(visitRecordMapper.selectById(11L)).thenReturn(visit());
        when(clinicDeptMapper.selectById(1L)).thenReturn(dept());
        when(clinicRoomMapper.selectById(2L)).thenReturn(room());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> queueStatusCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("CALLING", true, "请立即前往诊室", "请立即前往诊室报到，避免错过本次叫号"),
                org.junit.jupiter.params.provider.Arguments.of("MISSED", true, "已过号", "已过号，请尽快联系护士台处理"),
                org.junit.jupiter.params.provider.Arguments.of("COMPLETED", false, "本次就诊已完成", "本次就诊流程已完成，如需复诊请咨询工作人员"),
                org.junit.jupiter.params.provider.Arguments.of("CANCELLED", false, "排队已取消", "当前排队已取消，请咨询导诊台")
        );
    }

    private PatientQueueQueryDTO queryDTO() {
        PatientQueueQueryDTO dto = new PatientQueueQueryDTO();
        dto.setPatientNo("P0000001234");
        dto.setPhoneSuffix("1234");
        return dto;
    }

    private PatientInfo patient() {
        PatientInfo patientInfo = new PatientInfo();
        patientInfo.setId(7L);
        patientInfo.setPatientNo("P0000001234");
        patientInfo.setPatientName("张三");
        patientInfo.setPhone("13800001234");
        patientInfo.setCurrentVisitId(11L);
        patientInfo.setCurrentStatus("TRIAGED");
        return patientInfo;
    }

    private VisitRecord visit() {
        VisitRecord visitRecord = new VisitRecord();
        visitRecord.setId(11L);
        visitRecord.setPatientId(7L);
        visitRecord.setVisitNo("V202603200001");
        visitRecord.setStatus("TRIAGED");
        visitRecord.setCurrentDeptId(1L);
        visitRecord.setCurrentRoomId(2L);
        return visitRecord;
    }

    private QueueTicketVO queueTicket(String status) {
        QueueTicketVO ticket = new QueueTicketVO();
        ticket.setTicketNo("T-20260320-0001");
        ticket.setVisitId(11L);
        ticket.setPatientId(7L);
        ticket.setDeptId(1L);
        ticket.setRoomId(2L);
        ticket.setStatus(status);
        ticket.setTriageLevel(2);
        ticket.setRank(3L);
        ticket.setWaitingCount(5L);
        ticket.setEstimatedWaitMinutes(25L);
        ticket.setWaitedMinutes(8L);
        ticket.setEnqueueTime(LocalDateTime.now().minusMinutes(8));
        ticket.setCallTime(LocalDateTime.now().minusMinutes(2));
        return ticket;
    }

    private QueueRankVO rank(Long rank, Long waitingCount, Long estimatedWaitMinutes) {
        return QueueRankVO.builder()
                .ticketNo("T-20260320-0001")
                .status("WAITING")
                .rank(rank)
                .waitingCount(waitingCount)
                .estimatedWaitMinutes(estimatedWaitMinutes)
                .build();
    }

    private ClinicDept dept() {
        ClinicDept dept = new ClinicDept();
        dept.setId(1L);
        dept.setDeptName("急诊科");
        dept.setEnabled(1);
        return dept;
    }

    private ClinicRoom room() {
        ClinicRoom room = new ClinicRoom();
        room.setId(2L);
        room.setRoomName("急诊 2 诊室");
        room.setDoctorName("张医生");
        return room;
    }
}
