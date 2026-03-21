package com.hospital.triage.modules.queue.entity.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeptQueueSummaryVO {

    private Long deptId;
    private Long waitingCount;
    private List<QueueTicketVO> callingTickets;
    private List<QueueTicketVO> waitingTickets;
}
