package com.hospital.triage.modules.triage.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TriageAssessmentCreateDTO {

    private Long visitId;
    private String chiefComplaint;
    private String symptomTags;
    private BigDecimal bodyTemperature;
    private Integer heartRate;
    private String bloodPressure;
    private Integer bloodOxygen;
    private Integer age;
    private String gender;
    private Boolean elderly;
    private Boolean pregnant;
    private Boolean child;
    private Boolean disabled;
    private Boolean revisit;
    private Integer manualAdjustScore;
    private String assessor;
}
