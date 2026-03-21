package com.hospital.triage.modules.patient.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PatientQueueViewVO {

    private String patientName;
    private String patientNo;
    private Long patientId;
    private Long visitId;
    private String visitNo;
    private String visitStatus;
    private String visitStatusText;
    private String queueStatus;
    private String queueStatusText;
    private String queueMessage;
    private String ticketNo;
    private Long deptId;
    private String deptName;
    private Long roomId;
    private String roomName;
    private String doctorName;
    private Long rank;
    private Long waitingCount;
    private Long estimatedWaitMinutes;
    private Long waitedMinutes;
    private Integer triageLevel;
    private Integer aiSuggestedLevel;
    private Long aiSuggestedDeptId;
    private String aiSuggestedDeptName;
    private String aiRiskLevel;
    private List<String> aiRiskTags;
    private List<String> aiStructuredSymptoms;
    private String aiAdvice;
    private Double aiConfidence;
    private Boolean aiNeedManualReview;
    private String aiSource;
    private String aiModelVersion;
    private String priorityReason;
    private String queueStrategyMode;
    private Boolean surgePriorityApplied;
    private Boolean agingBoostApplied;
    private String aiPriorityAdvice;
    private LocalDateTime enqueueTime;
    private LocalDateTime callTime;
    private LocalDateTime completeTime;
    private boolean hasActiveQueue;
}
