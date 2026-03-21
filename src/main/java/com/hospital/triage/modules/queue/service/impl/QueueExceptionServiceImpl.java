package com.hospital.triage.modules.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.enums.QueueStatusEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.queue.entity.po.QueueTicket;
import com.hospital.triage.modules.queue.entity.vo.QueueExceptionVO;
import com.hospital.triage.modules.queue.mapper.QueueTicketMapper;
import com.hospital.triage.modules.queue.service.QueueExceptionService;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QueueExceptionServiceImpl implements QueueExceptionService {

    private final VisitRecordMapper visitRecordMapper;
    private final TriageAssessmentMapper triageAssessmentMapper;
    private final QueueTicketMapper queueTicketMapper;
    private final PatientInfoMapper patientInfoMapper;
    private final ClinicDeptMapper clinicDeptMapper;

    public QueueExceptionServiceImpl(VisitRecordMapper visitRecordMapper,
                                     TriageAssessmentMapper triageAssessmentMapper,
                                     QueueTicketMapper queueTicketMapper,
                                     PatientInfoMapper patientInfoMapper,
                                     ClinicDeptMapper clinicDeptMapper) {
        this.visitRecordMapper = visitRecordMapper;
        this.triageAssessmentMapper = triageAssessmentMapper;
        this.queueTicketMapper = queueTicketMapper;
        this.patientInfoMapper = patientInfoMapper;
        this.clinicDeptMapper = clinicDeptMapper;
    }

    @Override
    public List<QueueExceptionVO> listUnqueuedTriaged(Long deptId) {
        List<VisitRecord> triagedVisits = visitRecordMapper.selectList(new LambdaQueryWrapper<VisitRecord>()
                        .eq(VisitRecord::getStatus, VisitStatusEnum.TRIAGED.name())
                        .eq(deptId != null, VisitRecord::getCurrentDeptId, deptId)
                        .orderByDesc(VisitRecord::getUpdatedTime, VisitRecord::getId))
                .stream()
                .filter(visit -> visit.getId() != null)
                .toList();
        if (triagedVisits.isEmpty()) {
            return List.of();
        }

        List<Long> visitIds = triagedVisits.stream().map(VisitRecord::getId).toList();
        Map<Long, TriageAssessment> latestAssessments = triageAssessmentMapper.selectList(new LambdaQueryWrapper<TriageAssessment>()
                        .in(TriageAssessment::getVisitId, visitIds)
                        .orderByDesc(TriageAssessment::getAssessedTime, TriageAssessment::getId))
                .stream()
                .collect(Collectors.toMap(TriageAssessment::getVisitId, Function.identity(), (current, ignored) -> current));
        Map<Long, QueueTicket> activeTickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                        .in(QueueTicket::getVisitId, visitIds)
                        .in(QueueTicket::getStatus,
                                QueueStatusEnum.WAITING.name(),
                                QueueStatusEnum.CALLING.name(),
                                QueueStatusEnum.MISSED.name())
                        .orderByDesc(QueueTicket::getUpdatedTime, QueueTicket::getId))
                .stream()
                .collect(Collectors.toMap(QueueTicket::getVisitId, Function.identity(), (current, ignored) -> current));
        Map<Long, PatientInfo> patientIndex = patientInfoMapper.selectBatchIds(triagedVisits.stream()
                        .map(VisitRecord::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientInfo::getId, Function.identity(), (current, ignored) -> current));
        Map<Long, ClinicDept> deptIndex = clinicDeptMapper.selectBatchIds(triagedVisits.stream()
                        .flatMap(visit -> java.util.stream.Stream.of(
                                visit.getCurrentDeptId(),
                                latestAssessments.get(visit.getId()) == null ? null : latestAssessments.get(visit.getId()).getRecommendDeptId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ClinicDept::getId, Function.identity(), (current, ignored) -> current));

        return triagedVisits.stream()
                .filter(visit -> latestAssessments.containsKey(visit.getId()))
                .filter(visit -> !activeTickets.containsKey(visit.getId()))
                .map(visit -> toVO(visit, latestAssessments.get(visit.getId()), patientIndex.get(visit.getPatientId()), deptIndex))
                .toList();
    }

    @Override
    public long countUnqueuedTriaged(Long deptId) {
        return listUnqueuedTriaged(deptId).size();
    }

    private QueueExceptionVO toVO(VisitRecord visitRecord,
                                  TriageAssessment assessment,
                                  PatientInfo patientInfo,
                                  Map<Long, ClinicDept> deptIndex) {
        QueueExceptionVO vo = new QueueExceptionVO();
        vo.setVisitId(visitRecord.getId());
        vo.setVisitNo(visitRecord.getVisitNo());
        vo.setPatientId(visitRecord.getPatientId());
        vo.setPatientNo(patientInfo == null ? null : patientInfo.getPatientNo());
        vo.setPatientName(patientInfo == null ? null : patientInfo.getPatientName());
        vo.setChiefComplaint(visitRecord.getChiefComplaint());
        vo.setTriageLevel(assessment.getTriageLevel());
        vo.setAssessmentId(assessment.getId());
        vo.setAssessedTime(assessment.getAssessedTime());
        vo.setDeptId(visitRecord.getCurrentDeptId());
        vo.setDeptName(resolveDeptName(deptIndex, visitRecord.getCurrentDeptId()));
        vo.setRecommendDeptId(assessment.getRecommendDeptId());
        vo.setRecommendDeptName(resolveDeptName(deptIndex, assessment.getRecommendDeptId()));
        vo.setReason("已完成分诊，当前尚未生成有效排队票据");
        return vo;
    }

    private String resolveDeptName(Map<Long, ClinicDept> deptIndex, Long deptId) {
        if (deptId == null) {
            return null;
        }
        ClinicDept dept = deptIndex.get(deptId);
        return dept == null ? null : dept.getDeptName();
    }
}
