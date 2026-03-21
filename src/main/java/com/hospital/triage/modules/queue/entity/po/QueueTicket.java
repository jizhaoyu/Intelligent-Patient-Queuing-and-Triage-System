package com.hospital.triage.modules.queue.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("queue_ticket")
public class QueueTicket extends BaseEntity {

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
    private String sourceType;
    private String sourceRemark;
    private String lastAdjustReason;
    private LocalDateTime enqueueTime;
    private LocalDateTime callTime;
    private LocalDateTime completeTime;
}
