package com.hospital.triage.modules.visit.entity.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VisitVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /**
     * 患者业务编号（例如 P 开头），方便前端展示
     */
    private String patientNo;

    private String visitNo;
    private String status;
    private LocalDateTime registerTime;
    private LocalDateTime arrivalTime;
    private String chiefComplaint;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentDeptId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentRoomId;
}
