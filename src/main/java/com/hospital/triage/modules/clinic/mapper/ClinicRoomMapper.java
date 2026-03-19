package com.hospital.triage.modules.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.triage.modules.clinic.entity.po.ClinicRoom;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClinicRoomMapper extends BaseMapper<ClinicRoom> {
}
