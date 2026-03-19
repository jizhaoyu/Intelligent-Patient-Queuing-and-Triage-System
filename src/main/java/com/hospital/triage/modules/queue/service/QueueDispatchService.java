package com.hospital.triage.modules.queue.service;

import com.hospital.triage.modules.queue.entity.dto.QueueTicketCreateDTO;
import com.hospital.triage.modules.queue.entity.vo.DeptQueueSummaryVO;
import com.hospital.triage.modules.queue.entity.vo.QueueRankVO;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;

public interface QueueDispatchService {

    QueueTicketVO createTicket(QueueTicketCreateDTO dto);

    QueueTicketVO getTicket(String ticketNo);

    DeptQueueSummaryVO waitingList(Long deptId);

    QueueTicketVO callNext(Long roomId, String operatorName);

    QueueTicketVO recall(String ticketNo, String operatorName);

    QueueTicketVO markMissed(String ticketNo, String operatorName);

    QueueTicketVO complete(String ticketNo, String operatorName);

    QueueTicketVO cancel(String ticketNo, String operatorName);

    QueueRankVO rank(String ticketNo);
}
