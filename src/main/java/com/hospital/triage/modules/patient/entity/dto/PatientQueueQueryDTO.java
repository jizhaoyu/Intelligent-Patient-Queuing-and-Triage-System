package com.hospital.triage.modules.patient.entity.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PatientQueueQueryDTO {

    private String patientNo;

    private String patientName;

    @Pattern(regexp = "\\d{4}", message = "手机号后4位格式不正确")
    private String phoneSuffix;
}
