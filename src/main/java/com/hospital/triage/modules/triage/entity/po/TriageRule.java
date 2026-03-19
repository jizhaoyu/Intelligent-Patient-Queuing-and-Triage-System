package com.hospital.triage.modules.triage.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("triage_rule")
public class TriageRule extends BaseEntity {

    private String ruleCode;
    private String ruleName;
    private String symptomKeyword;
    private Integer triageLevel;
    private Long recommendDeptId;
    private Integer specialWeight;
    private Integer fastTrack;
    private Integer enabled;
}
