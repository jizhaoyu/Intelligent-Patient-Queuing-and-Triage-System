package com.hospital.triage.modules.triage.entity.vo;

import lombok.Data;

@Data
public class TriageRuleVO {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String symptomKeyword;
    private Integer triageLevel;
    private Long recommendDeptId;
    private Integer specialWeight;
    private Integer fastTrack;
    private Integer enabled;
}
