package com.hospital.triage.modules.triage.entity.dto;

import lombok.Data;

@Data
public class TriageRuleUpdateDTO {

    private String ruleName;
    private String symptomKeyword;
    private Integer triageLevel;
    private Long recommendDeptId;
    private Integer specialWeight;
    private Integer fastTrack;
    private Integer enabled;
}
