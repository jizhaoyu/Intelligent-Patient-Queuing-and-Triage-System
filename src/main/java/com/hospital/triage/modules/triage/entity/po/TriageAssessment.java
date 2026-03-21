package com.hospital.triage.modules.triage.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("triage_assessment")
public class TriageAssessment extends BaseEntity {

    private Long visitId;
    private String symptomTags;
    private BigDecimal bodyTemperature;
    private Integer heartRate;
    private String bloodPressure;
    private Integer bloodOxygen;
    private Integer age;
    private String gender;
    private Boolean elderly;
    private Boolean pregnant;
    private Boolean child;
    private Boolean disabled;
    private Boolean revisit;
    private Integer triageLevel;
    private Long recommendDeptId;
    private Integer priorityScore;
    private Integer fastTrack;
    private Integer manualAdjustScore;
    private Integer aiSuggestedLevel;
    private Long aiSuggestedDeptId;
    private Integer aiPriorityScore;
    private String aiRiskLevel;
    private String aiRiskTags;
    private BigDecimal aiConfidence;
    private String aiAdvice;
    private Boolean aiNeedManualReview;
    private String aiRuleDiff;
    private String aiModelVersion;
    private String aiSource;
    private Long aiAuditId;
    private String assessor;
    private LocalDateTime assessedTime;
}
