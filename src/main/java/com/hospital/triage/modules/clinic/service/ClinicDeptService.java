package com.hospital.triage.modules.clinic.service;

import com.hospital.triage.modules.clinic.entity.vo.ClinicDeptOptionVO;

import java.util.List;

public interface ClinicDeptService {

    List<ClinicDeptOptionVO> listOptions();

    Long getDeptIdByRoomId(Long roomId);
}
