package com.hospital.triage.modules.triage.service;

import com.hospital.triage.modules.triage.entity.dto.TriageAssessmentCreateDTO;
import com.hospital.triage.modules.triage.entity.vo.TriageAssessmentVO;

public interface TriageAssessmentService {

    TriageAssessmentVO create(TriageAssessmentCreateDTO dto);

    TriageAssessmentVO getById(Long id);

    TriageAssessmentVO reassess(Long id, TriageAssessmentCreateDTO dto);
}
