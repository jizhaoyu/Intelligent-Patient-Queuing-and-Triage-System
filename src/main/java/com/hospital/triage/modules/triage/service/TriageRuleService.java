package com.hospital.triage.modules.triage.service;

import com.hospital.triage.modules.triage.entity.dto.TriageRuleUpdateDTO;
import com.hospital.triage.modules.triage.entity.vo.TriageRuleVO;

import java.util.List;

public interface TriageRuleService {

    List<TriageRuleVO> list();

    TriageRuleVO update(Long id, TriageRuleUpdateDTO dto);
}
