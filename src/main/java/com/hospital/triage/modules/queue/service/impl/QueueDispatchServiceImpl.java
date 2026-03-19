package com.hospital.triage.modules.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.constant.RedisKeyConstants;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.common.enums.QueueEventTypeEnum;
import com.hospital.triage.common.enums.QueueStatusEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.queue.entity.dto.QueueTicketCreateDTO;
import com.hospital.triage.modules.queue.entity.po.QueueEventLog;
import com.hospital.triage.modules.queue.entity.po.QueueTicket;
import com.hospital.triage.modules.queue.entity.vo.DeptQueueSummaryVO;
import com.hospital.triage.modules.queue.entity.vo.QueueClaimResult;
import com.hospital.triage.modules.queue.entity.vo.QueueRankVO;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;
import com.hospital.triage.modules.queue.mapper.QueueEventLogMapper;
import com.hospital.triage.modules.queue.mapper.QueueTicketMapper;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class QueueDispatchServiceImpl implements QueueDispatchService {

    private final QueueTicketMapper queueTicketMapper;
    private final QueueEventLogMapper queueEventLogMapper;
    private final TriageAssessmentMapper triageAssessmentMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppQueueProperties appQueueProperties;
    private final DefaultRedisScript<String> callNextRedisScript;

    public QueueDispatchServiceImpl(QueueTicketMapper queueTicketMapper,
                                    QueueEventLogMapper queueEventLogMapper,
                                    TriageAssessmentMapper triageAssessmentMapper,
                                    VisitRecordMapper visitRecordMapper,
                                    RedisTemplate<String, Object> redisTemplate,
                                    AppQueueProperties appQueueProperties,
                                    DefaultRedisScript<String> callNextRedisScript) {
        this.queueTicketMapper = queueTicketMapper;
        this.queueEventLogMapper = queueEventLogMapper;
        this.triageAssessmentMapper = triageAssessmentMapper;
        this.visitRecordMapper = visitRecordMapper;
        this.redisTemplate = redisTemplate;
        this.appQueueProperties = appQueueProperties;
        this.callNextRedisScript = callNextRedisScript;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO createTicket(QueueTicketCreateDTO dto) {
        VisitRecord visitRecord = requireVisit(dto.getVisitId());
        TriageAssessment assessment = requireAssessment(dto.getAssessmentId());
        QueueTicket ticket = new QueueTicket();
        ticket.setTicketNo(nextTicketNo(assessment.getRecommendDeptId()));
        ticket.setVisitId(visitRecord.getId());
        ticket.setPatientId(visitRecord.getPatientId());
        ticket.setAssessmentId(assessment.getId());
        ticket.setDeptId(assessment.getRecommendDeptId());
        ticket.setRoomId(dto.getRoomId());
        ticket.setTriageLevel(assessment.getTriageLevel());
        ticket.setPriorityScore(assessment.getPriorityScore());
        ticket.setFastTrack(assessment.getFastTrack());
        ticket.setStatus(QueueStatusEnum.WAITING.name());
        ticket.setRecallCount(0);
        ticket.setEnqueueTime(LocalDateTime.now());
        queueTicketMapper.insert(ticket);
        syncWaitingTicket(ticket);
        recordEvent(ticket.getTicketNo(), QueueEventTypeEnum.ENQUEUE, null, QueueStatusEnum.WAITING.name(), ticket.getRoomId(), "system", "入队");
        visitRecord.setStatus(VisitStatusEnum.QUEUING.name());
        visitRecord.setCurrentDeptId(ticket.getDeptId());
        visitRecord.setCurrentRoomId(ticket.getRoomId());
        visitRecordMapper.updateById(visitRecord);
        return enrich(ticket);
    }

    @Override
    public QueueTicketVO getTicket(String ticketNo) {
        return enrich(requireTicket(ticketNo));
    }

    @Override
    public DeptQueueSummaryVO waitingList(Long deptId) {
        List<QueueTicket> waitingTickets = listWaitingTickets(deptId);
        return DeptQueueSummaryVO.builder()
                .deptId(deptId)
                .waitingCount((long) waitingTickets.size())
                .waitingTickets(waitingTickets.stream().map(this::enrich).toList())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO callNext(Long roomId, String operatorName) {
        Long deptId = resolveDeptIdByRoom(roomId);
        int retryTimes = Math.max(appQueueProperties.getCallNextRetryTimes() == null ? 1 : appQueueProperties.getCallNextRetryTimes(), 1);
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            QueueClaimResult claimResult = claimNext(roomId, deptId);
            if (claimResult == null || !StringUtils.hasText(claimResult.getTicketNo())) {
                break;
            }

            QueueTicket ticket = getTicketOrNull(claimResult.getTicketNo());
            if (ticket == null) {
                clearCallingTicket(roomId, claimResult.getTicketNo());
                continue;
            }
            if (!Objects.equals(ticket.getStatus(), QueueStatusEnum.WAITING.name())) {
                removeTicketFromWaitingRedis(ticket.getTicketNo(), ticket.getDeptId(), ticket.getRoomId(), ticket.getStatus());
                clearCallingTicket(roomId, claimResult.getTicketNo());
                continue;
            }

            Long waitingRoomId = ticket.getRoomId();
            ticket.setStatus(QueueStatusEnum.CALLING.name());
            ticket.setCallTime(LocalDateTime.now());
            ticket.setRoomId(roomId);
            if (queueTicketMapper.updateById(ticket) == 0) {
                handleClaimUpdateFailure(roomId, claimResult.getTicketNo(), waitingRoomId);
                continue;
            }

            removeTicketFromWaitingRedis(ticket.getTicketNo(), ticket.getDeptId(), waitingRoomId, ticket.getStatus());
            refreshCallingTicket(roomId, ticket.getTicketNo());
            recordEvent(ticket.getTicketNo(), QueueEventTypeEnum.CALL_NEXT, QueueStatusEnum.WAITING.name(), QueueStatusEnum.CALLING.name(), roomId, operatorName, "叫号");
            VisitRecord visitRecord = requireVisit(ticket.getVisitId());
            visitRecord.setStatus(VisitStatusEnum.IN_TREATMENT.name());
            visitRecord.setCurrentRoomId(roomId);
            visitRecordMapper.updateById(visitRecord);
            return enrich(ticket);
        }
        throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "暂无待叫号患者");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO recall(String ticketNo, String operatorName) {
        QueueTicket ticket = requireTicket(ticketNo);
        if (!Objects.equals(ticket.getStatus(), QueueStatusEnum.CALLING.name()) && !Objects.equals(ticket.getStatus(), QueueStatusEnum.MISSED.name())) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前状态不可复呼");
        }
        int recallCount = ticket.getRecallCount() == null ? 0 : ticket.getRecallCount();
        if (recallCount >= appQueueProperties.getRecallLimit()) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "复呼次数已达上限");
        }
        ticket.setRecallCount(recallCount + 1);
        ticket.setStatus(QueueStatusEnum.CALLING.name());
        ticket.setCallTime(LocalDateTime.now());
        queueTicketMapper.updateById(ticket);
        refreshCallingTicket(ticket.getRoomId(), ticket.getTicketNo());
        recordEvent(ticketNo, QueueEventTypeEnum.RECALL, QueueStatusEnum.MISSED.name(), QueueStatusEnum.CALLING.name(), ticket.getRoomId(), operatorName, "复呼");
        return enrich(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO markMissed(String ticketNo, String operatorName) {
        QueueTicket ticket = requireTicket(ticketNo);
        if (!Objects.equals(ticket.getStatus(), QueueStatusEnum.CALLING.name())) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前状态不可标记过号");
        }
        changeStatus(ticket, QueueStatusEnum.MISSED, QueueEventTypeEnum.MISSED, ticket.getRoomId(), operatorName, "过号");
        queueTicketMapper.updateById(ticket);
        return enrich(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO complete(String ticketNo, String operatorName) {
        QueueTicket ticket = requireTicket(ticketNo);
        if (!Objects.equals(ticket.getStatus(), QueueStatusEnum.CALLING.name())) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前状态不可完成");
        }
        changeStatus(ticket, QueueStatusEnum.COMPLETED, QueueEventTypeEnum.COMPLETE, ticket.getRoomId(), operatorName, "完成接诊");
        ticket.setCompleteTime(LocalDateTime.now());
        queueTicketMapper.updateById(ticket);
        VisitRecord visitRecord = requireVisit(ticket.getVisitId());
        visitRecord.setStatus(VisitStatusEnum.COMPLETED.name());
        visitRecordMapper.updateById(visitRecord);
        return enrich(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO cancel(String ticketNo, String operatorName) {
        QueueTicket ticket = requireTicket(ticketNo);
        if (Objects.equals(ticket.getStatus(), QueueStatusEnum.COMPLETED.name()) || Objects.equals(ticket.getStatus(), QueueStatusEnum.CANCELLED.name())) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前状态不可取消");
        }
        changeStatus(ticket, QueueStatusEnum.CANCELLED, QueueEventTypeEnum.CANCEL, ticket.getRoomId(), operatorName, "取消排队");
        queueTicketMapper.updateById(ticket);
        VisitRecord visitRecord = requireVisit(ticket.getVisitId());
        visitRecord.setStatus(VisitStatusEnum.CANCELLED.name());
        visitRecordMapper.updateById(visitRecord);
        return enrich(ticket);
    }

    @Override
    public QueueRankVO rank(String ticketNo) {
        QueueTicket ticket = requireTicket(ticketNo);
        List<QueueTicket> waitingTickets = listWaitingTickets(ticket.getDeptId());
        long rankIndex = waitingTickets.stream().map(QueueTicket::getTicketNo).toList().indexOf(ticketNo);
        long normalizedRank = rankIndex >= 0 ? rankIndex + 1 : 0;
        long waitingCount = waitingTickets.stream()
                .filter(item -> !item.getTicketNo().equals(ticketNo))
                .count();
        return QueueRankVO.builder()
                .ticketNo(ticketNo)
                .status(ticket.getStatus())
                .rank(normalizedRank)
                .waitingCount(waitingCount)
                .estimatedWaitMinutes(normalizedRank == 0 ? 0 : waitingCount * appQueueProperties.getEstimateMinutesPerPerson())
                .build();
    }

    double calculateQueueScore(QueueTicket ticket, LocalDateTime now) {
        long waitingMinutes = ticket.getEnqueueTime() == null ? 0 : Math.max(Duration.between(ticket.getEnqueueTime(), now).toMinutes(), 0);
        long agingScore = waitingMinutes * appQueueProperties.getAgingScorePerMinute();
        return -(ticket.getPriorityScore() == null ? 0 : ticket.getPriorityScore()) * 1_000_000D
                - agingScore * 1_000D
                + waitingMinutes;
    }

    private void changeStatus(QueueTicket ticket,
                              QueueStatusEnum targetStatus,
                              QueueEventTypeEnum eventType,
                              Long roomId,
                              String operatorName,
                              String remark) {
        String fromStatus = ticket.getStatus();
        ticket.setStatus(targetStatus.name());
        if (targetStatus == QueueStatusEnum.WAITING) {
            syncWaitingTicket(ticket);
        } else {
            removeWaitingTicket(ticket);
        }
        recordEvent(ticket.getTicketNo(), eventType, fromStatus, targetStatus.name(), roomId, operatorName, remark);
    }

    private void syncWaitingTicket(QueueTicket ticket) {
        String deptKey = String.format(RedisKeyConstants.QUEUE_DEPT_ACTIVE, ticket.getDeptId());
        redisTemplate.opsForZSet().add(deptKey, ticket.getTicketNo(), calculateQueueScore(ticket, LocalDateTime.now()));
        if (ticket.getRoomId() != null) {
            String roomKey = String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, ticket.getRoomId());
            redisTemplate.opsForZSet().add(roomKey, ticket.getTicketNo(), calculateQueueScore(ticket, LocalDateTime.now()));
        }
        redisTemplate.opsForHash().put(String.format(RedisKeyConstants.QUEUE_TICKET, ticket.getTicketNo()), "status", ticket.getStatus());
        redisTemplate.opsForHash().put(String.format(RedisKeyConstants.QUEUE_TICKET, ticket.getTicketNo()), "priorityScore", ticket.getPriorityScore());
    }

    private void removeWaitingTicket(QueueTicket ticket) {
        removeTicketFromWaitingRedis(ticket.getTicketNo(), ticket.getDeptId(), ticket.getRoomId(), ticket.getStatus());
        if (ticket.getRoomId() != null && !QueueStatusEnum.CALLING.name().equals(ticket.getStatus())) {
            redisTemplate.delete(String.format(RedisKeyConstants.QUEUE_CALLING, ticket.getRoomId()));
        }
    }

    private void removeTicketFromWaitingRedis(String ticketNo, Long deptId, Long waitingRoomId, String status) {
        if (deptId != null) {
            redisTemplate.opsForZSet().remove(String.format(RedisKeyConstants.QUEUE_DEPT_ACTIVE, deptId), ticketNo);
        }
        if (waitingRoomId != null) {
            redisTemplate.opsForZSet().remove(String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, waitingRoomId), ticketNo);
        }
        redisTemplate.opsForHash().put(String.format(RedisKeyConstants.QUEUE_TICKET, ticketNo), "status", status);
    }

    private QueueClaimResult claimNext(Long roomId, Long deptId) {
        String claim = redisTemplate.execute(callNextRedisScript,
                List.of(
                        String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, roomId),
                        String.format(RedisKeyConstants.QUEUE_DEPT_ACTIVE, deptId),
                        String.format(RedisKeyConstants.QUEUE_CALLING, roomId)
                ),
                String.valueOf(appQueueProperties.getCallingTtlSeconds()),
                String.valueOf(Boolean.TRUE.equals(appQueueProperties.getAllowDeptFallback())));
        if (!StringUtils.hasText(claim)) {
            return null;
        }
        int splitIndex = claim.indexOf(':');
        if (splitIndex < 0) {
            return QueueClaimResult.builder().ticketNo(claim).build();
        }
        return QueueClaimResult.builder()
                .source(claim.substring(0, splitIndex))
                .ticketNo(claim.substring(splitIndex + 1))
                .build();
    }

    private void handleClaimUpdateFailure(Long roomId, String ticketNo, Long waitingRoomId) {
        QueueTicket latest = getTicketOrNull(ticketNo);
        if (latest != null) {
            if (Objects.equals(latest.getStatus(), QueueStatusEnum.WAITING.name())) {
                syncWaitingTicket(latest);
            } else {
                removeTicketFromWaitingRedis(ticketNo, latest.getDeptId(), waitingRoomId, latest.getStatus());
            }
        }
        clearCallingTicket(roomId, ticketNo);
    }

    private void clearCallingTicket(Long roomId, String ticketNo) {
        String callingKey = String.format(RedisKeyConstants.QUEUE_CALLING, roomId);
        Object current = redisTemplate.opsForValue().get(callingKey);
        if (current != null && Objects.equals(ticketNo, String.valueOf(current))) {
            redisTemplate.delete(callingKey);
        }
    }

    private void refreshCallingTicket(Long roomId, String ticketNo) {
        redisTemplate.opsForValue().set(String.format(RedisKeyConstants.QUEUE_CALLING, roomId), ticketNo,
                appQueueProperties.getCallingTtlSeconds(), TimeUnit.SECONDS);
    }

    private List<QueueTicket> listWaitingTickets(Long deptId) {
        return listWaitingTicketsByDept(deptId);
    }

    private List<QueueTicket> listWaitingTicketsByDept(Long deptId) {
        return queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                        .eq(QueueTicket::getDeptId, deptId)
                        .eq(QueueTicket::getStatus, QueueStatusEnum.WAITING.name()))
                .stream()
                .sorted((a, b) -> Double.compare(calculateQueueScore(a, LocalDateTime.now()), calculateQueueScore(b, LocalDateTime.now())))
                .toList();
    }

    private List<QueueTicket> listWaitingTicketsByRoom(Long roomId) {
        return queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                        .eq(QueueTicket::getRoomId, roomId)
                        .eq(QueueTicket::getStatus, QueueStatusEnum.WAITING.name()))
                .stream()
                .sorted((a, b) -> Double.compare(calculateQueueScore(a, LocalDateTime.now()), calculateQueueScore(b, LocalDateTime.now())))
                .toList();
    }

    private String nextTicketNo(Long deptId) {
        String date = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = String.format(RedisKeyConstants.QUEUE_SEQ, date, deptId);
        Long seq = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.DAYS);
        long sequence = seq == null ? 1L : seq;
        return date + "-" + deptId + "-" + String.format("%04d", sequence);
    }

    private void recordEvent(String ticketNo,
                             QueueEventTypeEnum eventType,
                             String fromStatus,
                             String toStatus,
                             Long roomId,
                             String operatorName,
                             String remark) {
        QueueEventLog eventLog = new QueueEventLog();
        eventLog.setTicketNo(ticketNo);
        eventLog.setEventType(eventType.name());
        eventLog.setFromStatus(fromStatus);
        eventLog.setToStatus(toStatus);
        eventLog.setRoomId(roomId);
        eventLog.setOperatorName(operatorName);
        eventLog.setRemark(remark);
        queueEventLogMapper.insert(eventLog);
    }

    private QueueTicket requireTicket(String ticketNo) {
        QueueTicket ticket = getTicketOrNull(ticketNo);
        if (ticket == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "排队票据不存在");
        }
        return ticket;
    }

    private QueueTicket getTicketOrNull(String ticketNo) {
        return queueTicketMapper.selectOne(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getTicketNo, ticketNo)
                .last("limit 1"));
    }

    private VisitRecord requireVisit(Long visitId) {
        VisitRecord visitRecord = visitRecordMapper.selectById(visitId);
        if (visitRecord == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "到诊记录不存在");
        }
        return visitRecord;
    }

    private TriageAssessment requireAssessment(Long assessmentId) {
        TriageAssessment assessment = triageAssessmentMapper.selectById(assessmentId);
        if (assessment == null) {
            throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "分诊评估不存在");
        }
        return assessment;
    }

    private Long resolveDeptIdByRoom(Long roomId) {
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, roomId), 0, 0);
        if (tuples != null && !tuples.isEmpty()) {
            Object value = tuples.iterator().next().getValue();
            if (value != null) {
                QueueTicket ticket = requireTicket(String.valueOf(value));
                return ticket.getDeptId();
            }
        }
        QueueTicket latest = queueTicketMapper.selectOne(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getRoomId, roomId)
                .orderByDesc(QueueTicket::getUpdatedTime)
                .last("limit 1"));
        if (latest != null) {
            return latest.getDeptId();
        }
        throw new ServiceException(ErrorCodeEnum.NOT_FOUND.getCode(), "诊室未绑定候诊科室");
    }

    private QueueTicketVO enrich(QueueTicket ticket) {
        QueueTicketVO vo = new QueueTicketVO();
        BeanUtils.copyProperties(ticket, vo);
        QueueRankVO rankVO = rank(ticket.getTicketNo());
        vo.setRank(rankVO.getRank());
        vo.setWaitingCount(rankVO.getWaitingCount());
        vo.setEstimatedWaitMinutes(rankVO.getEstimatedWaitMinutes());
        return vo;
    }
}
