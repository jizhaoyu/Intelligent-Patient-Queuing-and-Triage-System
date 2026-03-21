package com.hospital.triage.modules.triage.service;

import com.hospital.triage.modules.triage.service.model.PatientTriageAiRequest;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiResult;

public interface PatientTriageAiService {

    PatientTriageAiResult analyze(PatientTriageAiRequest request);

    Long saveAudit(Long visitId,
                   Long assessmentId,
                   PatientTriageAiRequest request,
                   PatientTriageAiResult result,
                   Integer finalTriageLevel,
                   Integer finalPriorityScore,
                   Boolean adopted);
}

