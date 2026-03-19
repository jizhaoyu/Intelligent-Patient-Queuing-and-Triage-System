package com.hospital.triage.modules.visit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VisitRecordMapper extends BaseMapper<VisitRecord> {
}
