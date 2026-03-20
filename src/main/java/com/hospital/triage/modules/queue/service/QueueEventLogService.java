package com.hospital.triage.modules.queue.service;

import com.hospital.triage.modules.queue.entity.vo.QueueEventLogVO;

import java.util.List;

public interface QueueEventLogService {

    List<QueueEventLogVO> list(String ticketNo, String eventType);
}
