package com.hospital.triage.modules.patient.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.patient.entity.dto.PatientQueueQueryDTO;
import com.hospital.triage.modules.patient.entity.dto.PatientSelfQueueEnrollDTO;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueViewVO;
import com.hospital.triage.modules.patient.service.PatientQueueQueryService;
import com.hospital.triage.modules.patient.service.PatientSelfQueueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-queue")
public class PatientQueueController {

    private final PatientQueueQueryService patientQueueQueryService;
    private final PatientSelfQueueService patientSelfQueueService;

    public PatientQueueController(PatientQueueQueryService patientQueueQueryService,
                                  PatientSelfQueueService patientSelfQueueService) {
        this.patientQueueQueryService = patientQueueQueryService;
        this.patientSelfQueueService = patientSelfQueueService;
    }

    @PostMapping("/query")
    public Result<PatientQueueViewVO> query(@Valid @RequestBody PatientQueueQueryDTO dto) {
        return Result.success(patientQueueQueryService.query(dto));
    }

    @PostMapping("/enroll")
    public Result<PatientQueueViewVO> enroll(@Valid @RequestBody PatientSelfQueueEnrollDTO dto) {
        return Result.success(patientSelfQueueService.enroll(dto));
    }
}
