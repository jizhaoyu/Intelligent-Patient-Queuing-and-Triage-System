package com.hospital.triage.modules.triage.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("triage_ai_audit")
public class TriageAiAudit extends BaseEntity {

    private Long visitId;
    private Long assessmentId;
    private String scene;
    private String chiefComplaint;
    private String symptomTags;
    private Integer aiSuggestedLevel;
    private Long aiSuggestedDeptId;
    private String aiSuggestedDeptName;
    private Integer aiPriorityScore;
    private String aiRiskLevel;
    private String aiRiskTags;
    private BigDecimal aiConfidence;
    private Boolean aiNeedManualReview;
    private String aiAdvice;
    private String aiRuleDiff;
    private String aiSource;
    private String aiProvider;
    private String aiModelVersion;
    private String requestPayload;
    private String responsePayload;
    private String errorMessage;
    private Boolean adopted;
    private Integer finalTriageLevel;
    private Integer finalPriorityScore;
}

