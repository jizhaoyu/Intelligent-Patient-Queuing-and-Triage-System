package com.hospital.triage.modules.triage.entity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TriageAssessmentVO {

    private Long id;
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
    private String recommendDeptName;
    private Integer priorityScore;
    private Integer fastTrack;
    private Integer manualAdjustScore;
    private Integer aiSuggestedLevel;
    private Long aiSuggestedDeptId;
    private String aiSuggestedDeptName;
    private Integer aiPriorityScore;
    private String aiRiskLevel;
    private List<String> aiRiskTags;
    private String aiAdvice;
    private Double aiConfidence;
    private Boolean aiNeedManualReview;
    private String aiRuleDiff;
    private String aiSource;
    private String aiModelVersion;
    private String assessor;
    private LocalDateTime assessedTime;
    private Boolean queueCreated;
    private String queueTicketNo;
    private String queueStatus;
    private Long queueDeptId;
    private Long queueRoomId;
    private String queueDeptName;
    private String queueRoomName;
}
