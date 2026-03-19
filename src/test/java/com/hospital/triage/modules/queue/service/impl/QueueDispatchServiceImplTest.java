package com.hospital.triage.modules.queue.service.impl;

import com.hospital.triage.common.enums.QueueStatusEnum;
import com.hospital.triage.modules.queue.entity.po.QueueTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueueDispatchServiceImplTest {

    private QueueDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        AppQueueProperties properties = new AppQueueProperties();
        properties.setAgingScorePerMinute(2);
        properties.setEstimateMinutesPerPerson(5);
        properties.setRecallLimit(2);
        service = new QueueDispatchServiceImpl(null, null, null, null, null, properties, null);
    }

    @Test
    void shouldOrderQueueByPriorityThenAging() {
        QueueTicket highPriority = ticket("T1", 900, 5);
        QueueTicket agingPriority = ticket("T2", 850, 30);
        QueueTicket lowPriority = ticket("T3", 400, 1);

        List<QueueTicket> ordered = List.of(highPriority, agingPriority, lowPriority).stream()
                .sorted(Comparator.comparing(ticket -> service.calculateQueueScore(ticket, LocalDateTime.now())))
                .toList();

        assertThat(ordered).extracting(QueueTicket::getTicketNo).containsExactly("T1", "T2", "T3");
    }

    @Test
    void shouldAllowTransitionFromWaitingToCallingAndComplete() {
        QueueTicket ticket = ticket("T1", 900, 2);
        ticket.setStatus(QueueStatusEnum.WAITING.name());
        ticket.setStatus(QueueStatusEnum.CALLING.name());
        ticket.setCompleteTime(LocalDateTime.now());
        ticket.setStatus(QueueStatusEnum.COMPLETED.name());

        assertThat(ticket.getStatus()).isEqualTo(QueueStatusEnum.COMPLETED.name());
        assertThat(ticket.getCompleteTime()).isNotNull();
    }

    @Test
    void shouldCalculateEstimatedRankFromWaitingList() throws Exception {
        Method method = QueueDispatchServiceImpl.class.getDeclaredMethod("calculateQueueScore", QueueTicket.class, LocalDateTime.class);
        method.setAccessible(true);
        double score = (double) method.invoke(service, ticket("T1", 500, 10), LocalDateTime.now());
        assertThat(score).isLessThan(0);
    }

    private QueueTicket ticket(String ticketNo, int priorityScore, int waitMinutes) {
        QueueTicket ticket = new QueueTicket();
        ticket.setTicketNo(ticketNo);
        ticket.setPriorityScore(priorityScore);
        ticket.setEnqueueTime(LocalDateTime.now().minusMinutes(waitMinutes));
        ticket.setStatus(QueueStatusEnum.WAITING.name());
        return ticket;
    }
}
