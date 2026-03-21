package com.hospital.triage.modules.patient.service;

import com.hospital.triage.modules.patient.entity.dto.PatientQueueQueryDTO;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueViewVO;

public interface PatientQueueQueryService {

    PatientQueueViewVO query(PatientQueueQueryDTO dto);
}
