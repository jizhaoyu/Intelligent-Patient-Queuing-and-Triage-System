package com.hospital.triage.modules.visit.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VisitVO {

    private Long id;
    private Long patientId;
    private String visitNo;
    private String status;
    private LocalDateTime registerTime;
    private LocalDateTime arrivalTime;
    private String chiefComplaint;
    private Long currentDeptId;
    private Long currentRoomId;
}
