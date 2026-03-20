package com.hospital.triage.modules.queue.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.auth.security.AuthenticatedUser;
import com.hospital.triage.modules.queue.entity.dto.QueueTicketCreateDTO;
import com.hospital.triage.modules.queue.entity.vo.DeptQueueSummaryVO;
import com.hospital.triage.modules.queue.entity.vo.QueueEventLogVO;
import com.hospital.triage.modules.queue.entity.vo.QueueRankVO;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.queue.service.QueueEventLogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final QueueDispatchService queueDispatchService;
    private final QueueEventLogService queueEventLogService;

    public QueueController(QueueDispatchService queueDispatchService,
                           QueueEventLogService queueEventLogService) {
        this.queueDispatchService = queueDispatchService;
        this.queueEventLogService = queueEventLogService;
    }

    @PostMapping("/tickets")
    @PreAuthorize("hasAuthority('queue:manage')")
    public Result<QueueTicketVO> create(@Valid @RequestBody QueueTicketCreateDTO dto) {
        return Result.success(queueDispatchService.createTicket(dto));
    }

    @GetMapping("/tickets/{ticketNo}")
    @PreAuthorize("hasAuthority('queue:manage') or hasAuthority('queue:call')")
    public Result<QueueTicketVO> getTicket(@PathVariable String ticketNo) {
        return Result.success(queueDispatchService.getTicket(ticketNo));
    }

    @GetMapping("/depts/{deptId}/waiting")
    public Result<DeptQueueSummaryVO> waiting(@PathVariable Long deptId) {
        return Result.success(queueDispatchService.waitingList(deptId));
    }

    @PostMapping("/rooms/{roomId}/call-next")
    @PreAuthorize("hasAuthority('queue:call')")
    public Result<QueueTicketVO> callNext(@PathVariable Long roomId, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(queueDispatchService.callNext(roomId, user == null ? "system" : user.getUsername()));
    }

    @PostMapping("/tickets/{ticketNo}/recall")
    @PreAuthorize("hasAuthority('queue:call')")
    public Result<QueueTicketVO> recall(@PathVariable String ticketNo, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(queueDispatchService.recall(ticketNo, user == null ? "system" : user.getUsername()));
    }

    @PostMapping("/tickets/{ticketNo}/missed")
    @PreAuthorize("hasAuthority('queue:call')")
    public Result<QueueTicketVO> missed(@PathVariable String ticketNo, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(queueDispatchService.markMissed(ticketNo, user == null ? "system" : user.getUsername()));
    }

    @PostMapping("/tickets/{ticketNo}/complete")
    @PreAuthorize("hasAuthority('queue:call')")
    public Result<QueueTicketVO> complete(@PathVariable String ticketNo, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(queueDispatchService.complete(ticketNo, user == null ? "system" : user.getUsername()));
    }

    @PostMapping("/tickets/{ticketNo}/cancel")
    @PreAuthorize("hasAuthority('queue:manage')")
    public Result<QueueTicketVO> cancel(@PathVariable String ticketNo, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(queueDispatchService.cancel(ticketNo, user == null ? "system" : user.getUsername()));
    }

    @GetMapping("/tickets/{ticketNo}/rank")
    @PreAuthorize("hasAuthority('queue:manage') or hasAuthority('dashboard:view')")
    public Result<QueueRankVO> rank(@PathVariable String ticketNo) {
        return Result.success(queueDispatchService.rank(ticketNo));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('queue:manage')")
    public Result<List<QueueEventLogVO>> events(@RequestParam(required = false) String ticketNo,
                                                @RequestParam(required = false) String eventType) {
        return Result.success(queueEventLogService.list(ticketNo, eventType));
    }
}
