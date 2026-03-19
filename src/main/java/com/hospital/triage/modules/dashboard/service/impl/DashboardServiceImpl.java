package com.hospital.triage.modules.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.modules.dashboard.entity.vo.DeptDashboardSummaryVO;
import com.hospital.triage.modules.dashboard.entity.vo.RoomCurrentVO;
import com.hospital.triage.modules.dashboard.service.DashboardService;
import com.hospital.triage.modules.queue.entity.po.QueueTicket;
import com.hospital.triage.modules.queue.mapper.QueueTicketMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final QueueTicketMapper queueTicketMapper;

    public DashboardServiceImpl(QueueTicketMapper queueTicketMapper) {
        this.queueTicketMapper = queueTicketMapper;
    }

    @Override
    public DeptDashboardSummaryVO deptSummary(Long deptId) {
        List<QueueTicket> tickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getDeptId, deptId));
        long waitingCount = tickets.stream().filter(ticket -> "WAITING".equals(ticket.getStatus())).count();
        long callingCount = tickets.stream().filter(ticket -> "CALLING".equals(ticket.getStatus())).count();
        long completedCount = tickets.stream().filter(ticket -> "COMPLETED".equals(ticket.getStatus())).count();
        long averageWaitMinutes = (long) tickets.stream()
                .filter(ticket -> ticket.getEnqueueTime() != null && ticket.getCallTime() != null)
                .mapToLong(ticket -> Duration.between(ticket.getEnqueueTime(), ticket.getCallTime()).toMinutes())
                .average()
                .orElse(0);
        long timeoutHighPriorityCount = tickets.stream()
                .filter(ticket -> ticket.getTriageLevel() != null && ticket.getTriageLevel() <= 2)
                .filter(ticket -> ticket.getEnqueueTime() != null && Duration.between(ticket.getEnqueueTime(), LocalDateTime.now()).toMinutes() > 30)
                .count();
        return DeptDashboardSummaryVO.builder()
                .deptId(deptId)
                .waitingCount(waitingCount)
                .callingCount(callingCount)
                .completedCount(completedCount)
                .averageWaitMinutes(averageWaitMinutes)
                .timeoutHighPriorityCount(timeoutHighPriorityCount)
                .build();
    }

    @Override
    public RoomCurrentVO currentRoom(Long roomId) {
        QueueTicket ticket = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                        .eq(QueueTicket::getRoomId, roomId)
                        .eq(QueueTicket::getStatus, "CALLING"))
                .stream()
                .max(Comparator.comparing(QueueTicket::getCallTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (ticket == null) {
            return RoomCurrentVO.builder().roomId(roomId).status("IDLE").build();
        }
        return RoomCurrentVO.builder()
                .roomId(roomId)
                .ticketNo(ticket.getTicketNo())
                .status(ticket.getStatus())
                .patientId(ticket.getPatientId())
                .triageLevel(ticket.getTriageLevel())
                .priorityScore(ticket.getPriorityScore())
                .build();
    }
}
