package com.hospital.triage.modules.dashboard.entity.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeptDashboardSummaryVO {

    private Long deptId;
    private Long waitingCount;
    private Long callingCount;
    private Long completedCount;
    private Long averageWaitMinutes;
    private Long timeoutHighPriorityCount;
    private Long unqueuedTriagedCount;
}
