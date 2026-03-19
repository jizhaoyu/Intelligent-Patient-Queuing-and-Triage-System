package com.hospital.triage.modules.dashboard.entity.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomCurrentVO {

    private Long roomId;
    private String ticketNo;
    private String status;
    private Long patientId;
    private Integer triageLevel;
    private Integer priorityScore;
}
