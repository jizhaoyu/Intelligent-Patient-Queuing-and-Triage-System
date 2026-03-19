package com.hospital.triage.modules.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PatientInfoMapper extends BaseMapper<PatientInfo> {
}
