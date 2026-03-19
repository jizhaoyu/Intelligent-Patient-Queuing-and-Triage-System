package com.hospital.triage.modules.triage.entity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TriageAssessmentVO {

    private Long id;
    private Long visitId;
    private String symptomTags;
    private BigDecimal bodyTemperature;
    private Integer heartRate;
    private String bloodPressure;
    private Integer bloodOxygen;
    private Integer triageLevel;
    private Long recommendDeptId;
    private Integer priorityScore;
    private Integer fastTrack;
    private Integer manualAdjustScore;
    private String assessor;
    private LocalDateTime assessedTime;
}
