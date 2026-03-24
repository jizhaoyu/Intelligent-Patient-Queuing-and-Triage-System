package com.hospital.triage.modules.triage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.common.enums.TriageLevelEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.triage.entity.dto.TriageAssessmentCreateDTO;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import com.hospital.triage.modules.triage.entity.vo.TriageAssessmentVO;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.triage.mapper.TriageRuleMapper;
import com.hospital.triage.modules.triage.service.PatientTriageAiService;
import com.hospital.triage.modules.triage.service.TriageAssessmentService;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiRequest;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiResult;
import com.hospital.triage.modules.triage.service.support.DeptRoutingSupport;
import com.hospital.triage.modules.triage.service.support.TriageRuleMatchSupport;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import com.hospital.triage.modules.visit.service.VisitStatusSnapshotSyncService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class TriageAssessmentServiceImpl implements TriageAssessmentService {

    private final TriageAssessmentMapper triageAssessmentMapper;
    private final TriageRuleMapper triageRuleMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final ClinicDeptMapper clinicDeptMapper;
    private final QueueDispatchService queueDispatchService;
    private final VisitStatusSnapshotSyncService visitStatusSnapshotSyncService;
    private final PatientTriageAiService patientTriageAiService;

    public TriageAssessmentServiceImpl(TriageAssessmentMapper triageAssessmentMapper,
                                       TriageRuleMapper triageRuleMapper,
                                       VisitRecordMapper visitRecordMapper,
                                       ClinicDeptMapper clinicDeptMapper,
                                       QueueDispatchService queueDispatchService,
                                       VisitStatusSnapshotSyncService visitStatusSnapshotSyncService,
                                       PatientTriageAiService patientTriageAiService) {
        this.triageAssessmentMapper = triageAssessmentMapper;
        this.triageRuleMapper = triageRuleMapper;
        this.visitRecordMapper = visitRecordMapper;
        this.clinicDeptMapper = clinicDeptMapper;
        this.queueDispatchService = queueDispatchService;
        this.visitStatusSnapshotSyncService = visitStatusSnapshotSyncService;
        this.patientTriageAiService = patientTriageAiService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageAssessmentVO create(TriageAssessmentCreateDTO dto) {
        validateCreateRequest(dto);
        VisitRecord visitRecord = requireAssessableVisit(dto.getVisitId(), false);
        TriageRule rule = sanitizeMatchedRule(dto, matchRule(dto.getChiefComplaint(), dto.getSymptomTags()));
        Integer triageLevel = determineLevel(dto, rule);
        Long recommendDeptId = recommendDeptId(dto, rule);
        validateRecommendDeptId(recommendDeptId);
        Integer priorityScore = calculatePriorityScore(dto, triageLevel, visitRecord.getArrivalTime(), rule);
        PatientTriageAiRequest aiRequest = buildTriageAiRequest(dto, visitRecord, triageLevel, recommendDeptId);
        PatientTriageAiResult aiResult = patientTriageAiService.analyze(aiRequest);

        TriageAssessment assessment = new TriageAssessment();
        BeanUtils.copyProperties(dto, assessment);
        assessment.setTriageLevel(triageLevel);
        assessment.setRecommendDeptId(recommendDeptId);
        assessment.setPriorityScore(priorityScore);
        assessment.setFastTrack(isFastTrack(dto, rule) ? 1 : 0);
        applyAiSuggestion(assessment, aiResult);
        LocalDateTime now = LocalDateTime.now();
        assessment.setAssessedTime(now);
        triageAssessmentMapper.insert(assessment);
        Long aiAuditId = patientTriageAiService.saveAudit(visitRecord.getId(), assessment.getId(),
                aiRequest, aiResult, assessment.getTriageLevel(), assessment.getPriorityScore(), isAiAdopted(assessment, aiResult));
        if (aiAuditId != null) {
            assessment.setAiAuditId(aiAuditId);
            triageAssessmentMapper.updateById(assessment);
        }
        updateVisitAfterAssessment(visitRecord, assessment, now);
        return toVO(assessment, queueDispatchService.enqueueAfterTriage(visitRecord.getId(), assessment.getId()));
    }

    @Override
    public TriageAssessmentVO getById(Long id) {
        TriageAssessment assessment = triageAssessmentMapper.selectById(id);
        if (assessment == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "分诊评估不存在");
        }
        return toVO(assessment, queueDispatchService.getLatestTicketByVisitId(assessment.getVisitId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageAssessmentVO reassess(Long id, TriageAssessmentCreateDTO dto) {
        TriageAssessment assessment = triageAssessmentMapper.selectById(id);
        if (assessment == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "分诊评估不存在");
        }
        validateReassessRequest(dto, assessment);
        VisitRecord visitRecord = requireAssessableVisit(assessment.getVisitId(), true);
        TriageRule rule = sanitizeMatchedRule(dto, matchRule(dto.getChiefComplaint(), dto.getSymptomTags()));
        Integer triageLevel = determineLevel(dto, rule);
        Long recommendDeptId = recommendDeptId(dto, rule);
        validateRecommendDeptId(recommendDeptId);
        Integer priorityScore = calculatePriorityScore(dto, triageLevel, visitRecord.getArrivalTime(), rule);
        PatientTriageAiRequest aiRequest = buildTriageAiRequest(dto, visitRecord, triageLevel, recommendDeptId);
        PatientTriageAiResult aiResult = patientTriageAiService.analyze(aiRequest);

        BeanUtils.copyProperties(dto, assessment, "id", "visitId", "createdTime", "updatedTime", "deleted", "version");
        assessment.setTriageLevel(triageLevel);
        assessment.setRecommendDeptId(recommendDeptId);
        assessment.setPriorityScore(priorityScore);
        assessment.setFastTrack(isFastTrack(dto, rule) ? 1 : 0);
        applyAiSuggestion(assessment, aiResult);
        LocalDateTime now = LocalDateTime.now();
        assessment.setAssessedTime(now);
        triageAssessmentMapper.updateById(assessment);
        Long aiAuditId = patientTriageAiService.saveAudit(visitRecord.getId(), assessment.getId(),
                aiRequest, aiResult, assessment.getTriageLevel(), assessment.getPriorityScore(), isAiAdopted(assessment, aiResult));
        if (aiAuditId != null) {
            assessment.setAiAuditId(aiAuditId);
            triageAssessmentMapper.updateById(assessment);
        }
        updateVisitAfterAssessment(visitRecord, assessment, now);
        return toVO(assessment, queueDispatchService.enqueueAfterTriage(visitRecord.getId(), assessment.getId()));
    }

    int calculatePriorityScore(TriageAssessmentCreateDTO dto, Integer triageLevel, LocalDateTime arrivalTime, TriageRule rule) {
        int levelWeight = TriageLevelEnum.fromLevel(triageLevel).getWeight();
        int specialWeight = calculateSpecialWeight(dto, rule);
        int waitAgingScore = calculateWaitAgingScore(arrivalTime);
        int manualAdjustScore = dto.getManualAdjustScore() == null ? 0 : dto.getManualAdjustScore();
        return levelWeight + specialWeight + waitAgingScore + manualAdjustScore;
    }

    int calculateWaitAgingScore(LocalDateTime arrivalTime) {
        if (arrivalTime == null) {
            return 0;
        }
        long waitingMinutes = Math.max(Duration.between(arrivalTime, LocalDateTime.now()).toMinutes(), 0);
        return (int) Math.min(waitingMinutes * 2, 200);
    }

    private int calculateSpecialWeight(TriageAssessmentCreateDTO dto, TriageRule rule) {
        int weight = rule != null && rule.getSpecialWeight() != null ? rule.getSpecialWeight() : 0;
        if (Boolean.TRUE.equals(dto.getElderly())) {
            weight += 40;
        }
        if (Boolean.TRUE.equals(dto.getPregnant())) {
            weight += 80;
        }
        if (Boolean.TRUE.equals(dto.getChild())) {
            weight += 60;
        }
        if (Boolean.TRUE.equals(dto.getDisabled())) {
            weight += 50;
        }
        if (Boolean.TRUE.equals(dto.getRevisit())) {
            weight += 20;
        }
        return weight;
    }

    private Integer determineLevel(TriageAssessmentCreateDTO dto, TriageRule rule) {
        if (dto.getBloodOxygen() != null && dto.getBloodOxygen() < 90) {
            return 1;
        }
        if (dto.getHeartRate() != null && dto.getHeartRate() > 140) {
            return 2;
        }
        if (dto.getBodyTemperature() != null && dto.getBodyTemperature().doubleValue() >= 39.5D) {
            return 2;
        }
        if (rule != null && rule.getTriageLevel() != null) {
            return rule.getTriageLevel();
        }
        return 4;
    }

    private Long recommendDeptId(TriageAssessmentCreateDTO dto, TriageRule rule) {
        if (rule != null && rule.getRecommendDeptId() != null) {
            return rule.getRecommendDeptId();
        }
        return findDeptIdByCode(DeptRoutingSupport.recommendDeptCode(
                dto == null ? null : dto.getAge(),
                dto == null ? null : dto.getChild(),
                dto == null ? null : dto.getPregnant(),
                dto == null ? null : dto.getChiefComplaint(),
                dto == null ? null : dto.getSymptomTags()));
    }

    private TriageRule sanitizeMatchedRule(TriageAssessmentCreateDTO dto, TriageRule rule) {
        if (rule == null || isPediatricPatient(dto) || !isPediatricsRule(rule)) {
            return rule;
        }
        return null;
    }

    private boolean isPediatricPatient(TriageAssessmentCreateDTO dto) {
        if (dto == null) {
            return false;
        }
        return DeptRoutingSupport.isPediatricPatient(dto.getAge(), dto.getChild());
    }

    private boolean isPediatricsRule(TriageRule rule) {
        if (rule == null) {
            return false;
        }
        if (StringUtils.hasText(rule.getRuleCode())
                && rule.getRuleCode().trim().toUpperCase(Locale.ROOT).startsWith("RULE_PED")) {
            return true;
        }
        return isPediatricsDeptName(resolveDeptName(rule.getRecommendDeptId()));
    }

    private boolean isPediatricsDeptName(String deptName) {
        return StringUtils.hasText(deptName) && deptName.contains("儿科");
    }

    private boolean isFastTrack(TriageAssessmentCreateDTO dto, TriageRule rule) {
        return TriageLevelEnum.fromLevel(determineLevel(dto, rule)).getLevel() <= 2
                || (rule != null && Integer.valueOf(1).equals(rule.getFastTrack()));
    }

    private TriageRule matchRule(String chiefComplaint, String symptomTags) {
        if (!StringUtils.hasText(chiefComplaint) && !StringUtils.hasText(symptomTags)) {
            return null;
        }
        return TriageRuleMatchSupport.bestMatch(
                triageRuleMapper.selectList(new LambdaQueryWrapper<TriageRule>()
                        .eq(TriageRule::getEnabled, 1)),
                chiefComplaint,
                symptomTags);
    }

    private Long findDeptIdByCode(String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return null;
        }
        List<ClinicDept> depts = clinicDeptMapper.selectList(new LambdaQueryWrapper<ClinicDept>()
                .eq(ClinicDept::getDeptCode, deptCode)
                .eq(ClinicDept::getEnabled, 1)
                .last("limit 1"));
        if (depts.isEmpty()) {
            return null;
        }
        return depts.get(0).getId();
    }

    private void validateCreateRequest(TriageAssessmentCreateDTO dto) {
        if (dto.getVisitId() == null) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), "到诊ID不能为空");
        }
    }

    private void validateReassessRequest(TriageAssessmentCreateDTO dto, TriageAssessment assessment) {
        if (dto.getVisitId() != null && !dto.getVisitId().equals(assessment.getVisitId())) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), "重评估请求中的到诊ID与原评估记录不一致");
        }
    }

    private void validateRecommendDeptId(Long recommendDeptId) {
        if (recommendDeptId == null) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "分诊结果缺少推荐科室，无法自动入队");
        }
    }

    private VisitRecord requireVisit(Long visitId) {
        VisitRecord visitRecord = visitRecordMapper.selectById(visitId);
        if (visitRecord == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "到诊记录不存在");
        }
        return visitRecord;
    }

    private VisitRecord requireAssessableVisit(Long visitId, boolean reassess) {
        VisitRecord visitRecord = requireVisit(visitId);
        VisitStatusEnum status = VisitStatusEnum.valueOf(visitRecord.getStatus());
        if (status == VisitStatusEnum.COMPLETED || status == VisitStatusEnum.CANCELLED) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前就诊状态不可分诊");
        }
        if (!reassess && status != VisitStatusEnum.ARRIVED) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "仅已到诊记录允许首次分诊");
        }
        if (reassess && status != VisitStatusEnum.ARRIVED && status != VisitStatusEnum.TRIAGED && status != VisitStatusEnum.QUEUING) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前就诊状态不可重新评估");
        }
        return visitRecord;
    }

    private PatientTriageAiRequest buildTriageAiRequest(TriageAssessmentCreateDTO dto,
                                                        VisitRecord visitRecord,
                                                        Integer triageLevel,
                                                        Long recommendDeptId) {
        return PatientTriageAiRequest.builder()
                .scene("TRIAGE_ASSESSMENT")
                .visitId(visitRecord.getId())
                .chiefComplaint(StringUtils.hasText(dto.getChiefComplaint()) ? dto.getChiefComplaint() : dto.getSymptomTags())
                .symptomTags(dto.getSymptomTags())
                .age(dto.getAge())
                .gender(dto.getGender())
                .elderly(dto.getElderly())
                .pregnant(dto.getPregnant())
                .child(dto.getChild())
                .disabled(dto.getDisabled())
                .revisit(dto.getRevisit())
                .bodyTemperature(dto.getBodyTemperature())
                .heartRate(dto.getHeartRate())
                .bloodPressure(dto.getBloodPressure())
                .bloodOxygen(dto.getBloodOxygen())
                .currentTriageLevel(triageLevel)
                .currentRecommendDeptId(recommendDeptId)
                .currentRecommendDeptName(resolveDeptName(recommendDeptId))
                .selectedDeptId(recommendDeptId)
                .selectedDeptName(resolveDeptName(recommendDeptId))
                .build();
    }

    private void applyAiSuggestion(TriageAssessment assessment, PatientTriageAiResult aiResult) {
        if (aiResult == null) {
            return;
        }
        assessment.setAiSuggestedLevel(aiResult.getSuggestedLevel());
        assessment.setAiSuggestedDeptId(aiResult.getSuggestedDeptId());
        assessment.setAiPriorityScore(aiResult.getSuggestedPriorityScore());
        assessment.setAiRiskLevel(aiResult.getRiskLevel());
        assessment.setAiRiskTags(joinRiskTags(aiResult.getRiskTags()));
        if (aiResult.getConfidence() != null) {
            assessment.setAiConfidence(java.math.BigDecimal.valueOf(Math.max(0D, Math.min(1D, aiResult.getConfidence()))));
        } else {
            assessment.setAiConfidence(null);
        }
        assessment.setAiAdvice(aiResult.getAdvice());
        assessment.setAiNeedManualReview(Boolean.TRUE.equals(aiResult.getNeedManualReview()));
        assessment.setAiRuleDiff(aiResult.getRuleDiff());
        assessment.setAiModelVersion(aiResult.getModelVersion());
        assessment.setAiSource(aiResult.getSource());
    }

    private boolean isAiAdopted(TriageAssessment assessment, PatientTriageAiResult aiResult) {
        return aiResult != null
                && java.util.Objects.equals(assessment.getTriageLevel(), aiResult.getSuggestedLevel())
                && java.util.Objects.equals(assessment.getRecommendDeptId(), aiResult.getSuggestedDeptId());
    }

    private String joinRiskTags(List<String> riskTags) {
        if (riskTags == null || riskTags.isEmpty()) {
            return null;
        }
        String result = riskTags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(","));
        return StringUtils.hasText(result) ? result : null;
    }

    private List<String> splitRiskTags(String riskTags) {
        if (!StringUtils.hasText(riskTags)) {
            return List.of();
        }
        return java.util.Arrays.stream(riskTags.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void updateVisitAfterAssessment(VisitRecord visitRecord, TriageAssessment assessment, LocalDateTime statusUpdatedTime) {
        visitRecord.setStatus(VisitStatusEnum.TRIAGED.name());
        visitRecord.setCurrentDeptId(assessment.getRecommendDeptId());
        visitRecordMapper.updateById(visitRecord);
        visitStatusSnapshotSyncService.syncFromVisit(visitRecord, statusUpdatedTime);
    }

    private TriageAssessmentVO toVO(TriageAssessment assessment, com.hospital.triage.modules.queue.entity.vo.QueueTicketVO queueTicket) {
        TriageAssessmentVO vo = new TriageAssessmentVO();
        BeanUtils.copyProperties(assessment, vo, "aiRiskTags", "aiConfidence");
        vo.setRecommendDeptName(resolveDeptName(assessment.getRecommendDeptId()));
        vo.setAiSuggestedDeptName(resolveDeptName(assessment.getAiSuggestedDeptId()));
        vo.setAiRiskTags(splitRiskTags(assessment.getAiRiskTags()));
        vo.setAiConfidence(assessment.getAiConfidence() == null ? null : assessment.getAiConfidence().doubleValue());
        vo.setQueueCreated(queueTicket != null);
        if (queueTicket != null) {
            vo.setQueueTicketNo(queueTicket.getTicketNo());
            vo.setQueueStatus(queueTicket.getStatus());
            vo.setQueueDeptId(queueTicket.getDeptId());
            vo.setQueueRoomId(queueTicket.getRoomId());
            vo.setQueueDeptName(queueTicket.getDeptName());
            vo.setQueueRoomName(queueTicket.getRoomName());
        }
        return vo;
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        ClinicDept dept = clinicDeptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }
}
