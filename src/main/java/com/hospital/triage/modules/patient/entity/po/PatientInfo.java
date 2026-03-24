package com.hospital.triage.modules.patient.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.triage.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("patient_info")
public class PatientInfo extends BaseEntity {

    private String patientNo;
    private String patientName;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String idCard;
    private String allergyHistory;
    private String specialTags;
    private Boolean priorityRevisitPending;
    private LocalDateTime priorityRevisitGrantedTime;
    private String priorityRevisitGrantedBy;
    private String currentStatus;
    private Long currentVisitId;
    private String currentVisitNo;
    private Long currentDeptId;
    private Long currentRoomId;
    private LocalDateTime statusUpdatedTime;
}
