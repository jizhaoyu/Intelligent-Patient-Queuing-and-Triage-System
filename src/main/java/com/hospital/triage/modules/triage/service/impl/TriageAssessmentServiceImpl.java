package com.hospital.triage.modules.triage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.common.enums.TriageLevelEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.triage.entity.dto.TriageAssessmentCreateDTO;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import com.hospital.triage.modules.triage.entity.vo.TriageAssessmentVO;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.triage.mapper.TriageRuleMapper;
import com.hospital.triage.modules.triage.service.TriageAssessmentService;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TriageAssessmentServiceImpl implements TriageAssessmentService {

    private final TriageAssessmentMapper triageAssessmentMapper;
    private final TriageRuleMapper triageRuleMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final ClinicDeptMapper clinicDeptMapper;

    public TriageAssessmentServiceImpl(TriageAssessmentMapper triageAssessmentMapper,
                                       TriageRuleMapper triageRuleMapper,
                                       VisitRecordMapper visitRecordMapper,
                                       ClinicDeptMapper clinicDeptMapper) {
        this.triageAssessmentMapper = triageAssessmentMapper;
        this.triageRuleMapper = triageRuleMapper;
        this.visitRecordMapper = visitRecordMapper;
        this.clinicDeptMapper = clinicDeptMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageAssessmentVO create(TriageAssessmentCreateDTO dto) {
        VisitRecord visitRecord = requireVisit(dto.getVisitId());
        TriageRule rule = matchRule(dto.getSymptomTags());
        TriageAssessment assessment = new TriageAssessment();
        BeanUtils.copyProperties(dto, assessment);
        assessment.setTriageLevel(determineLevel(dto, rule));
        assessment.setRecommendDeptId(recommendDeptId(dto, rule));
        assessment.setPriorityScore(calculatePriorityScore(dto, assessment.getTriageLevel(), visitRecord.getArrivalTime(), rule));
        assessment.setFastTrack(isFastTrack(dto, rule) ? 1 : 0);
        assessment.setAssessedTime(LocalDateTime.now());
        triageAssessmentMapper.insert(assessment);
        updateVisitAfterAssessment(visitRecord, assessment);
        return toVO(assessment);
    }

    @Override
    public TriageAssessmentVO getById(Long id) {
        TriageAssessment assessment = triageAssessmentMapper.selectById(id);
        if (assessment == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "分诊评估不存在");
        }
        return toVO(assessment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageAssessmentVO reassess(Long id, TriageAssessmentCreateDTO dto) {
        TriageAssessment assessment = triageAssessmentMapper.selectById(id);
        if (assessment == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "分诊评估不存在");
        }
        VisitRecord visitRecord = requireVisit(assessment.getVisitId());
        TriageRule rule = matchRule(dto.getSymptomTags());
        BeanUtils.copyProperties(dto, assessment, "id", "visitId", "createdTime", "updatedTime", "deleted", "version");
        assessment.setTriageLevel(determineLevel(dto, rule));
        assessment.setRecommendDeptId(recommendDeptId(dto, rule));
        assessment.setPriorityScore(calculatePriorityScore(dto, assessment.getTriageLevel(), visitRecord.getArrivalTime(), rule));
        assessment.setFastTrack(isFastTrack(dto, rule) ? 1 : 0);
        assessment.setAssessedTime(LocalDateTime.now());
        triageAssessmentMapper.updateById(assessment);
        updateVisitAfterAssessment(visitRecord, assessment);
        return toVO(assessment);
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
        String symptoms = dto.getSymptomTags() == null ? "" : dto.getSymptomTags().toLowerCase(Locale.ROOT);
        if (symptoms.contains("胸") || symptoms.contains("heart") || symptoms.contains("呼吸")) {
            return findDeptIdByKeyword("急诊");
        }
        if (symptoms.contains("儿") || Boolean.TRUE.equals(dto.getChild())) {
            return findDeptIdByKeyword("儿科");
        }
        if (symptoms.contains("孕") || Boolean.TRUE.equals(dto.getPregnant())) {
            return findDeptIdByKeyword("妇产");
        }
        return findDeptIdByKeyword("全科");
    }

    private boolean isFastTrack(TriageAssessmentCreateDTO dto, TriageRule rule) {
        return TriageLevelEnum.fromLevel(determineLevel(dto, rule)).getLevel() <= 2
                || (rule != null && Integer.valueOf(1).equals(rule.getFastTrack()));
    }

    private TriageRule matchRule(String symptomTags) {
        if (!StringUtils.hasText(symptomTags)) {
            return null;
        }
        String normalized = symptomTags.toLowerCase(Locale.ROOT);
        return triageRuleMapper.selectList(new LambdaQueryWrapper<TriageRule>()
                        .eq(TriageRule::getEnabled, 1))
                .stream()
                .filter(rule -> StringUtils.hasText(rule.getSymptomKeyword()))
                .filter(rule -> normalized.contains(rule.getSymptomKeyword().toLowerCase(Locale.ROOT)))
                .min(Comparator.comparing(rule -> rule.getTriageLevel() == null ? 4 : rule.getTriageLevel()))
                .orElse(null);
    }

    private Long findDeptIdByKeyword(String keyword) {
        List<ClinicDept> depts = clinicDeptMapper.selectList(new LambdaQueryWrapper<ClinicDept>()
                .like(ClinicDept::getDeptName, keyword)
                .eq(ClinicDept::getEnabled, 1)
                .last("limit 1"));
        if (depts.isEmpty()) {
            return null;
        }
        return depts.get(0).getId();
    }

    private VisitRecord requireVisit(Long visitId) {
        VisitRecord visitRecord = visitRecordMapper.selectById(visitId);
        if (visitRecord == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "到诊记录不存在");
        }
        return visitRecord;
    }

    private void updateVisitAfterAssessment(VisitRecord visitRecord, TriageAssessment assessment) {
        visitRecord.setStatus(VisitStatusEnum.TRIAGED.name());
        visitRecord.setCurrentDeptId(assessment.getRecommendDeptId());
        visitRecordMapper.updateById(visitRecord);
    }

    private TriageAssessmentVO toVO(TriageAssessment assessment) {
        TriageAssessmentVO vo = new TriageAssessmentVO();
        BeanUtils.copyProperties(assessment, vo);
        return vo;
    }
}
