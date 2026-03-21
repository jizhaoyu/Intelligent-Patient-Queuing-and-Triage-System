package com.hospital.triage.modules.queue.service;

import com.hospital.triage.modules.queue.entity.dto.QueueTicketCreateDTO;
import com.hospital.triage.modules.queue.entity.vo.DeptQueueSummaryVO;
import com.hospital.triage.modules.queue.entity.vo.QueueRankVO;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;

import java.util.List;

public interface QueueDispatchService {

    QueueTicketVO createTicket(QueueTicketCreateDTO dto);

    QueueTicketVO enqueueAfterTriage(Long visitId, Long assessmentId);

    QueueTicketVO enqueueFromKiosk(Long visitId, Long assessmentId);

    QueueTicketVO getLatestTicketByVisitId(Long visitId);

    QueueTicketVO getTicket(String ticketNo);

    DeptQueueSummaryVO waitingList(Long deptId);

    List<QueueTicketVO> listActiveTickets(Long deptId, Long roomId);

    QueueTicketVO callNext(Long roomId, String operatorName);

    QueueTicketVO recall(String ticketNo, String operatorName);

    QueueTicketVO markMissed(String ticketNo, String operatorName);

    QueueTicketVO complete(String ticketNo, String operatorName);

    QueueTicketVO cancel(String ticketNo, String operatorName);

    QueueRankVO rank(String ticketNo);

    QueueRankVO roomRank(String ticketNo);
}
