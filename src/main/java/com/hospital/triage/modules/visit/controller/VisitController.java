package com.hospital.triage.modules.visit.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.visit.entity.dto.VisitCreateDTO;
import com.hospital.triage.modules.visit.entity.vo.VisitVO;
import com.hospital.triage.modules.visit.service.VisitService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('visit:manage')")
    public Result<VisitVO> create(@Valid @RequestBody VisitCreateDTO dto) {
        return Result.success(visitService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('visit:manage')")
    public Result<VisitVO> getById(@PathVariable Long id) {
        return Result.success(visitService.getById(id));
    }

    @PostMapping("/{id}/arrive")
    @PreAuthorize("hasAuthority('visit:manage')")
    public Result<VisitVO> arrive(@PathVariable Long id) {
        return Result.success(visitService.arrive(id));
    }
}
