package com.hospital.triage.modules.queue.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QueueEventLogVO {

    private Long id;
    private String ticketNo;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long visitId;
    private Long patientId;
    private Long deptId;
    private Long roomId;
    private String operatorName;
    private String sourceType;
    private String sourceRemark;
    private String remark;
    private LocalDateTime createdTime;
}
