package com.hospital.triage.modules.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.triage.modules.queue.entity.po.QueueTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QueueTicketMapper extends BaseMapper<QueueTicket> {
}
