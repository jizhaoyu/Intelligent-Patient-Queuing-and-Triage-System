package com.hospital.triage.modules.patient.entity.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PatientVO {

    private Long id;
    private String patientNo;
    private String patientName;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String idCard;
    private String allergyHistory;
    private String specialTags;
    private LocalDateTime createdTime;
}
