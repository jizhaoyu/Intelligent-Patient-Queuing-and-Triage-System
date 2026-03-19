package com.hospital.triage.modules.patient.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientUpdateDTO {

    @NotBlank(message = "患者姓名不能为空")
    private String patientName;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String idCard;
    private String allergyHistory;
    private String specialTags;
}
