package com.hospital.triage.modules.visit.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("visit_record")
public class VisitRecord extends BaseEntity {

    private Long patientId;
    private String visitNo;
    private String status;
    private LocalDateTime registerTime;
    private LocalDateTime arrivalTime;
    private String chiefComplaint;
    private Long currentDeptId;
    private Long currentRoomId;
}
