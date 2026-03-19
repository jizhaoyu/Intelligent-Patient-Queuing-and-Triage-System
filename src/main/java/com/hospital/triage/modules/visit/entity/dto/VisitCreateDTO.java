package com.hospital.triage.modules.visit.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VisitCreateDTO {

    @NotNull(message = "患者ID不能为空")
    private Long patientId;

    @NotBlank(message = "主诉不能为空")
    private String chiefComplaint;
}
