package com.hospital.triage.modules.queue.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QueueTicketCreateDTO {

    @NotNull(message = "到诊ID不能为空")
    private Long visitId;

    @NotNull(message = "分诊评估ID不能为空")
    private Long assessmentId;

    private Long roomId;
}
