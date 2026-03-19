package com.hospital.triage.modules.queue.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QueueTicketVO {

    private String ticketNo;
    private Long visitId;
    private Long patientId;
    private Long assessmentId;
    private Long deptId;
    private Long roomId;
    private Integer triageLevel;
    private Integer priorityScore;
    private String status;
    private Integer recallCount;
    private Integer fastTrack;
    private Long waitingCount;
    private Long rank;
    private Long estimatedWaitMinutes;
    private LocalDateTime enqueueTime;
    private LocalDateTime callTime;
    private LocalDateTime completeTime;
}
