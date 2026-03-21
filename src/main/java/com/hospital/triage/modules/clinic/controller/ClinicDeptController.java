package com.hospital.triage.modules.clinic.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.clinic.entity.vo.ClinicDeptOptionVO;
import com.hospital.triage.modules.clinic.service.ClinicDeptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clinic/depts")
public class ClinicDeptController {

    private final ClinicDeptService clinicDeptService;

    public ClinicDeptController(ClinicDeptService clinicDeptService) {
        this.clinicDeptService = clinicDeptService;
    }

    @GetMapping("/options")
    public Result<List<ClinicDeptOptionVO>> options() {
        return Result.success(clinicDeptService.listOptions());
    }

    @GetMapping("/rooms/{roomId}")
    public Result<Long> roomDeptId(@PathVariable Long roomId) {
        return Result.success(clinicDeptService.getDeptIdByRoomId(roomId));
    }
}
