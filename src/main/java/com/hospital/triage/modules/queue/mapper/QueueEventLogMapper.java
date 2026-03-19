package com.hospital.triage.modules.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.triage.modules.queue.entity.po.QueueEventLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QueueEventLogMapper extends BaseMapper<QueueEventLog> {
}
