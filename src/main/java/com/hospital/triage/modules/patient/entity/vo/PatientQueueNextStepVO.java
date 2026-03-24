package com.hospital.triage.modules.patient.entity.vo;

import lombok.Data;

@Data
public class PatientQueueNextStepVO {

    private String stage;
    private String title;
    private String action;
    private String locationHint;
    private String urgency;
}
