package com.hospital.triage.modules.triage.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.triage.entity.dto.TriageAssessmentCreateDTO;
import com.hospital.triage.modules.triage.entity.dto.TriageRuleUpdateDTO;
import com.hospital.triage.modules.triage.entity.vo.TriageAssessmentVO;
import com.hospital.triage.modules.triage.entity.vo.TriageRuleVO;
import com.hospital.triage.modules.triage.service.TriageAssessmentService;
import com.hospital.triage.modules.triage.service.TriageRuleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageAssessmentService triageAssessmentService;
    private final TriageRuleService triageRuleService;

    public TriageController(TriageAssessmentService triageAssessmentService, TriageRuleService triageRuleService) {
        this.triageAssessmentService = triageAssessmentService;
        this.triageRuleService = triageRuleService;
    }

    @PostMapping("/assessments")
    @PreAuthorize("hasAuthority('triage:assess')")
    public Result<TriageAssessmentVO> create(@Valid @RequestBody TriageAssessmentCreateDTO dto) {
        return Result.success(triageAssessmentService.create(dto));
    }

    @GetMapping("/assessments/{id}")
    @PreAuthorize("hasAuthority('triage:assess')")
    public Result<TriageAssessmentVO> getById(@PathVariable Long id) {
        return Result.success(triageAssessmentService.getById(id));
    }

    @PostMapping("/assessments/{id}/reassess")
    @PreAuthorize("hasAuthority('triage:assess')")
    public Result<TriageAssessmentVO> reassess(@PathVariable Long id, @Valid @RequestBody TriageAssessmentCreateDTO dto) {
        return Result.success(triageAssessmentService.reassess(id, dto));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('triage:rule')")
    public Result<List<TriageRuleVO>> rules() {
        return Result.success(triageRuleService.list());
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('triage:rule')")
    public Result<TriageRuleVO> updateRule(@PathVariable Long id, @RequestBody TriageRuleUpdateDTO dto) {
        return Result.success(triageRuleService.update(id, dto));
    }
}
