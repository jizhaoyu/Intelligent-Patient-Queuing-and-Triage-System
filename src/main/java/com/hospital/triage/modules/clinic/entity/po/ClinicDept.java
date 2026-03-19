package com.hospital.triage.modules.clinic.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("clinic_dept")
public class ClinicDept extends BaseEntity {

    private String deptCode;
    private String deptName;
    private Integer enabled;
}
