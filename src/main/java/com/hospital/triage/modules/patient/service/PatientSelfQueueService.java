package com.hospital.triage.modules.patient.service;

import com.hospital.triage.modules.patient.entity.dto.PatientSelfQueueEnrollDTO;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueViewVO;

public interface PatientSelfQueueService {

    /**
     * 患者通过自助机发起排队/取号
     */
    PatientQueueViewVO enroll(PatientSelfQueueEnrollDTO dto);
}

