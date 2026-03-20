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
    private Long roomId;
    private String operatorName;
    private String remark;
    private LocalDateTime createdTime;
}
