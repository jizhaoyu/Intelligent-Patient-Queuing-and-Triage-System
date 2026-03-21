package com.hospital.triage.modules.patient.entity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientSelfQueueEnrollDTO {

    @Pattern(regexp = "EXISTING|NEW", message = "患者办理方式不正确")
    private String patientMode;

    private String patientNo;

    private String patientName;

    @Pattern(regexp = "\\d{4}", message = "手机号后4位格式不正确")
    private String phoneSuffix;

    @Pattern(regexp = "1\\d{10}", message = "手机号格式不正确")
    private String phone;

    private String gender;

    private LocalDate birthDate;

    private String idCard;

    private String allergyHistory;

    private String specialTags;

    @NotNull(message = "科室ID不能为空")
    private Long deptId;

    /**
     * 主诉/症状描述，可选
     */
    private String chiefComplaint;
}
