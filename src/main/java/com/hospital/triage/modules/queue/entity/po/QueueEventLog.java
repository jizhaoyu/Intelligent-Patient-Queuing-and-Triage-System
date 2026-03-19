package com.hospital.triage.modules.queue.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("queue_event_log")
public class QueueEventLog extends BaseEntity {

    private String ticketNo;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long roomId;
    private String operatorName;
    private String remark;
}
