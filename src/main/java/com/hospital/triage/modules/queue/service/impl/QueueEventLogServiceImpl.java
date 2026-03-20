package com.hospital.triage.modules.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.modules.queue.entity.po.QueueEventLog;
import com.hospital.triage.modules.queue.entity.vo.QueueEventLogVO;
import com.hospital.triage.modules.queue.mapper.QueueEventLogMapper;
import com.hospital.triage.modules.queue.service.QueueEventLogService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class QueueEventLogServiceImpl implements QueueEventLogService {

    private final QueueEventLogMapper queueEventLogMapper;

    public QueueEventLogServiceImpl(QueueEventLogMapper queueEventLogMapper) {
        this.queueEventLogMapper = queueEventLogMapper;
    }

    @Override
    public List<QueueEventLogVO> list(String ticketNo, String eventType) {
        String normalizedTicketNo = StringUtils.hasText(ticketNo) ? ticketNo.trim() : null;
        String normalizedEventType = StringUtils.hasText(eventType) ? eventType.trim().toUpperCase(Locale.ROOT) : null;
        return queueEventLogMapper.selectList(new LambdaQueryWrapper<QueueEventLog>()
                        .like(StringUtils.hasText(normalizedTicketNo), QueueEventLog::getTicketNo, normalizedTicketNo)
                        .eq(StringUtils.hasText(normalizedEventType), QueueEventLog::getEventType, normalizedEventType)
                        .orderByDesc(QueueEventLog::getCreatedTime)
                        .last("limit 200"))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private QueueEventLogVO toVO(QueueEventLog eventLog) {
        QueueEventLogVO vo = new QueueEventLogVO();
        BeanUtils.copyProperties(eventLog, vo);
        return vo;
    }
}
