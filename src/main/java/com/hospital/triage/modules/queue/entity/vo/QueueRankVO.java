package com.hospital.triage.modules.queue.entity.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueueRankVO {

    private String ticketNo;
    private String status;
    private Long rank;
    private Long waitingCount;
    private Long estimatedWaitMinutes;
}
