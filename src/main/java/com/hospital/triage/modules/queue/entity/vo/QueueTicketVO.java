package com.hospital.triage.modules.queue.entity.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QueueTicketVO {

    private String ticketNo;
    private Long visitId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;
    /**
     * 患者编号（对人友好的业务编号，例如 P 开头），便于在前端展示
     */
    private String patientNo;
    private String patientName;
    private Long assessmentId;
    private Long deptId;
    private String deptName;
    private Long roomId;
    private String roomName;
    private String doctorName;
    private String chiefComplaint;
    private Integer triageLevel;
    private Integer priorityScore;
    private String status;
    private String displayStatus;
    private String displayStatusText;
    private Boolean waitingForConsultation;
    private Integer recallCount;
    private Integer fastTrack;
    private String sourceType;
    private String sourceRemark;
    private String lastAdjustReason;
    private Boolean consultationLocked;
    private String roomAssignmentStatus;
    private String priorityReason;
    private String queueStrategyMode;
    private Boolean surgePriorityApplied;
    private Boolean agingBoostApplied;
    private String aiPriorityAdvice;
    private String aiAdvice;
    private Integer aiSuggestedLevel;
    private String aiRiskLevel;
    private Boolean aiNeedManualReview;
    private Long waitingCount;
    private Long rank;
    private Long estimatedWaitMinutes;
    /**
     * 已等待时长（分钟），基于 enqueueTime 及当前/叫号时间从数据库时间计算
     */
    private Long waitedMinutes;
    private LocalDateTime enqueueTime;
    private LocalDateTime callTime;
    private LocalDateTime completeTime;
}
