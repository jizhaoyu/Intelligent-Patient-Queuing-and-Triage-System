package com.hospital.triage.modules.patient.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.patient.entity.dto.PatientCreateDTO;
import com.hospital.triage.modules.patient.entity.dto.PatientUpdateDTO;
import com.hospital.triage.modules.patient.entity.vo.PatientVO;
import com.hospital.triage.modules.patient.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('patient:manage')")
    public Result<PatientVO> create(@Valid @RequestBody PatientCreateDTO dto) {
        return Result.success(patientService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:manage')")
    public Result<PatientVO> getById(@PathVariable Long id) {
        return Result.success(patientService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('patient:manage')")
    public Result<List<PatientVO>> list(@RequestParam(required = false) String keyword) {
        return Result.success(patientService.list(keyword));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:manage')")
    public Result<PatientVO> update(@PathVariable Long id, @Valid @RequestBody PatientUpdateDTO dto) {
        return Result.success(patientService.update(id, dto));
    }
}
