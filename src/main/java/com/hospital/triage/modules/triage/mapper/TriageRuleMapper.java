package com.hospital.triage.modules.triage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TriageRuleMapper extends BaseMapper<TriageRule> {
}
