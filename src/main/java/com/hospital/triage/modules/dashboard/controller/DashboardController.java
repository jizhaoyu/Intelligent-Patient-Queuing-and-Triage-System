package com.hospital.triage.modules.dashboard.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.dashboard.entity.vo.DeptDashboardSummaryVO;
import com.hospital.triage.modules.dashboard.entity.vo.RoomCurrentVO;
import com.hospital.triage.modules.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/depts/{deptId}/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<DeptDashboardSummaryVO> deptSummary(@PathVariable Long deptId) {
        return Result.success(dashboardService.deptSummary(deptId));
    }

    @GetMapping("/rooms/{roomId}/current")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<RoomCurrentVO> roomCurrent(@PathVariable Long roomId) {
        return Result.success(dashboardService.currentRoom(roomId));
    }
}
