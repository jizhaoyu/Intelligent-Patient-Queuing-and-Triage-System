package com.hospital.triage.modules.dashboard.service;

import com.hospital.triage.modules.dashboard.entity.vo.DeptDashboardSummaryVO;
import com.hospital.triage.modules.dashboard.entity.vo.RoomCurrentVO;

public interface DashboardService {

    DeptDashboardSummaryVO deptSummary(Long deptId);

    RoomCurrentVO currentRoom(Long roomId);
}
