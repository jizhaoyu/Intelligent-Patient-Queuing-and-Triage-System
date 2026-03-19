package com.hospital.triage.modules.clinic.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("clinic_room")
public class ClinicRoom extends BaseEntity {

    private Long deptId;
    private String roomCode;
    private String roomName;
    private String doctorName;
    private Integer enabled;
}
