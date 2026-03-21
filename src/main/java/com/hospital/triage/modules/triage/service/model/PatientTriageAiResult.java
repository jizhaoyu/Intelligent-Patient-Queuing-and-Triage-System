package com.hospital.triage.modules.triage.service.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PatientTriageAiResult {

    private Integer suggestedLevel;
    private Long suggestedDeptId;
    private String suggestedDeptName;
    private Integer suggestedPriorityScore;
    private String riskLevel;
    private List<String> riskTags;
    private Boolean needManualReview;
    private String advice;
    private Double confidence;
    private String ruleDiff;
    private String source;
    private String provider;
    private String modelVersion;
    private String rawRequest;
    private String rawResponse;
    private String errorMessage;
}

