package com.hospital.triage.modules.triage.service.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PatientTriageAiRequest {

    private String scene;
    private Long visitId;
    private String chiefComplaint;
    private String symptomTags;
    private Integer age;
    private String gender;
    private Boolean elderly;
    private Boolean pregnant;
    private Boolean child;
    private Boolean disabled;
    private Boolean revisit;
    private BigDecimal bodyTemperature;
    private Integer heartRate;
    private String bloodPressure;
    private Integer bloodOxygen;
    private Integer currentTriageLevel;
    private Long currentRecommendDeptId;
    private String currentRecommendDeptName;
    private Long selectedDeptId;
    private String selectedDeptName;
}

