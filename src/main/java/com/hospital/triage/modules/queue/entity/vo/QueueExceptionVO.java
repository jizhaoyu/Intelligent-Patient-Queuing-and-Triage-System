package com.hospital.triage.modules.queue.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QueueExceptionVO {

    private Long visitId;
    private String visitNo;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private String chiefComplaint;
    private Integer triageLevel;
    private Long assessmentId;
    private LocalDateTime assessedTime;
    private Long deptId;
    private String deptName;
    private Long recommendDeptId;
    private String recommendDeptName;
    private String reason;
}
