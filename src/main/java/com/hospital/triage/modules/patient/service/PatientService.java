package com.hospital.triage.modules.patient.service;

import com.hospital.triage.modules.patient.entity.dto.PatientCreateDTO;
import com.hospital.triage.modules.patient.entity.dto.PatientUpdateDTO;
import com.hospital.triage.modules.patient.entity.vo.PatientVO;

import java.util.List;

public interface PatientService {

    PatientVO create(PatientCreateDTO dto);

    PatientVO getById(Long id);

    List<PatientVO> list(String keyword);

    PatientVO update(Long id, PatientUpdateDTO dto);
}
