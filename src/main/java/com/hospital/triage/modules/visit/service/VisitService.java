package com.hospital.triage.modules.visit.service;

import com.hospital.triage.modules.visit.entity.dto.VisitCreateDTO;
import com.hospital.triage.modules.visit.entity.vo.VisitVO;

public interface VisitService {

    VisitVO create(VisitCreateDTO dto);

    VisitVO getById(Long id);

    VisitVO arrive(Long id);
}
