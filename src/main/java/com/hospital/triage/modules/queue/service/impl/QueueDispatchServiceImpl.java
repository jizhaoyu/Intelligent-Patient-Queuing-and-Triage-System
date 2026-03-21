package com.hospital.triage.modules.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.constant.RedisKeyConstants;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.common.enums.QueueEventTypeEnum;
import com.hospital.triage.common.enums.QueueSourceTypeEnum;
import com.hospital.triage.common.enums.QueueStatusEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.entity.po.ClinicRoom;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.clinic.mapper.ClinicRoomMapper;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
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
import com.hospital.triage.modules.visit.service.VisitStatusSnapshotSyncService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class QueueDispatchServiceImpl implements QueueDispatchService {

    private final QueueTicketMapper queueTicketMapper;
    private final QueueEventLogMapper queueEventLogMapper;
    private final ClinicDeptMapper clinicDeptMapper;
    private final ClinicRoomMapper clinicRoomMapper;
    private final PatientInfoMapper patientInfoMapper;
    private final TriageAssessmentMapper triageAssessmentMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final VisitStatusSnapshotSyncService visitStatusSnapshotSyncService;
    private static final StringRedisSerializer STRING_REDIS_SERIALIZER = new StringRedisSerializer();

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppQueueProperties appQueueProperties;
    private final DefaultRedisScript<String> callNextRedisScript;

    public QueueDispatchServiceImpl(QueueTicketMapper queueTicketMapper,
                                    QueueEventLogMapper queueEventLogMapper,
                                    ClinicDeptMapper clinicDeptMapper,
                                    ClinicRoomMapper clinicRoomMapper,
                                    PatientInfoMapper patientInfoMapper,
                                    TriageAssessmentMapper triageAssessmentMapper,
                                    VisitRecordMapper visitRecordMapper,
                                    VisitStatusSnapshotSyncService visitStatusSnapshotSyncService,
                                    RedisTemplate<String, Object> redisTemplate,
                                    AppQueueProperties appQueueProperties,
                                    DefaultRedisScript<String> callNextRedisScript) {
        this.queueTicketMapper = queueTicketMapper;
        this.queueEventLogMapper = queueEventLogMapper;
        this.clinicDeptMapper = clinicDeptMapper;
        this.clinicRoomMapper = clinicRoomMapper;
        this.patientInfoMapper = patientInfoMapper;
        this.triageAssessmentMapper = triageAssessmentMapper;
        this.visitRecordMapper = visitRecordMapper;
        this.visitStatusSnapshotSyncService = visitStatusSnapshotSyncService;
        this.redisTemplate = redisTemplate;
        this.appQueueProperties = appQueueProperties;
        this.callNextRedisScript = callNextRedisScript;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO createTicket(QueueTicketCreateDTO dto) {
        VisitRecord visitRecord = requireVisit(dto.getVisitId());
        validateVisitStatusForManualCreate(visitRecord);
        TriageAssessment assessment = requireAssessment(dto.getAssessmentId());
        validateAssessmentBinding(visitRecord, assessment);
        validateNoManualCreateConflict(findActiveTicketByVisitId(visitRecord.getId()));
        return upsertTicket(visitRecord, assessment, dto.getRoomId(), "system", "异常补录入队",
                QueueSourceTypeEnum.MANUAL_REPAIR, "管理员手工补录", "异常补录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO enqueueAfterTriage(Long visitId, Long assessmentId) {
        return upsertTicket(visitId, assessmentId, null, "system", "分诊自动入队",
                QueueSourceTypeEnum.TRIAGE_AUTO, "分诊自动入队", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO enqueueFromKiosk(Long visitId, Long assessmentId) {
        VisitRecord visitRecord = requireVisit(visitId);
        TriageAssessment assessment = requireAssessment(assessmentId);
        validateAssessmentBinding(visitRecord, assessment);
        Long roomId = pickRoomIdForKiosk(assessment);
        return upsertTicket(visitRecord, assessment, roomId, "kiosk", "院内自助机取号",
                QueueSourceTypeEnum.KIOSK, "院内自助机正式取号", null);
    }

    @Override
    public QueueTicketVO getLatestTicketByVisitId(Long visitId) {
        QueueTicket ticket = findLatestTicketByVisitId(visitId);
        return ticket == null ? null : enrich(ticket);
    }

    @Override
    public QueueTicketVO getTicket(String ticketNo) {
        return enrich(requireTicket(ticketNo));
    }

    @Override
    public DeptQueueSummaryVO waitingList(Long deptId) {
        List<QueueTicket> waitingTickets = listWaitingTickets(deptId);
        List<QueueTicket> callingTickets = listCallingTickets(deptId);
        Map<Long, String> patientNames = loadPatientNames(waitingTickets, callingTickets);
        Map<Long, String> patientNos = loadPatientNos(waitingTickets, callingTickets);
        Map<Long, String> deptNames = loadDeptNames(waitingTickets, callingTickets);
        Map<Long, ClinicRoom> roomIndex = loadRooms(waitingTickets, callingTickets);
        Map<Long, List<QueueTicket>> waitingTicketIndex = buildWaitingTicketIndex(waitingTickets);
        Map<Long, List<QueueTicket>> roomWaitingTicketIndex = buildRoomWaitingTicketIndex(waitingTickets);
        return DeptQueueSummaryVO.builder()
                .deptId(deptId)
                .waitingCount((long) waitingTickets.size())
                .callingTickets(enrichTickets(callingTickets, waitingTicketIndex, roomWaitingTicketIndex, patientNames, patientNos, deptNames, roomIndex))
                .waitingTickets(enrichTickets(waitingTickets, waitingTicketIndex, roomWaitingTicketIndex, patientNames, patientNos, deptNames, roomIndex))
                .build();
    }

    @Override
    public List<QueueTicketVO> listActiveTickets(Long deptId, Long roomId) {
        List<QueueTicket> waitingTickets = roomId != null ? listWaitingTicketsByRoom(roomId) : listWaitingTickets(deptId);
        List<QueueTicket> callingTickets = listCallingTickets(deptId, roomId);
        Map<Long, String> patientNames = loadPatientNames(waitingTickets, callingTickets);
        Map<Long, String> patientNos = loadPatientNos(waitingTickets, callingTickets);
        Map<Long, String> deptNames = loadDeptNames(waitingTickets, callingTickets);
        Map<Long, ClinicRoom> roomIndex = loadRooms(waitingTickets, callingTickets);
        Map<Long, List<QueueTicket>> waitingTicketIndex = buildWaitingTicketIndex(waitingTickets);
        Map<Long, List<QueueTicket>> roomWaitingTicketIndex = buildRoomWaitingTicketIndex(waitingTickets);
        List<QueueTicketVO> result = new ArrayList<>();
        result.addAll(enrichTickets(callingTickets, waitingTicketIndex, roomWaitingTicketIndex, patientNames, patientNos, deptNames, roomIndex));
        result.addAll(enrichTickets(waitingTickets, waitingTicketIndex, roomWaitingTicketIndex, patientNames, patientNos, deptNames, roomIndex));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueTicketVO callNext(Long roomId, String operatorName) {
        Long deptId = resolveDeptIdByRoom(roomId);
        int retryTimes = Math.max(appQueueProperties.getCallNextRetryTimes() == null ? 1 : appQueueProperties.getCallNextRetryTimes(), 1);
        int maxAttempts = retryTimes + 1;
        boolean waitingIndexRebuilt = false;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            clearStaleCallingTicket(roomId);
            QueueClaimResult claimResult = claimNext(roomId, deptId);
            if (claimResult == null || !StringUtils.hasText(claimResult.getTicketNo())) {
                if (!waitingIndexRebuilt && rebuildWaitingQueueIndex(roomId, deptId)) {
                    waitingIndexRebuilt = true;
                    continue;
                }
                break;
            }

            QueueTicket ticket = getTicketOrNull(claimResult.getTicketNo());
            if (ticket == null) {
                evictGhostTicketFromQueueIndex(claimResult.getTicketNo(), deptId, roomId);
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
            LocalDateTime now = ticket.getCallTime();
            recordEvent(ticket, QueueEventTypeEnum.CALL_NEXT, QueueStatusEnum.WAITING.name(), QueueStatusEnum.CALLING.name(), roomId, operatorName, "叫号");
            VisitRecord visitRecord = requireVisit(ticket.getVisitId());
            visitRecord.setStatus(VisitStatusEnum.IN_TREATMENT.name());
            visitRecord.setCurrentRoomId(roomId);
            visitRecordMapper.updateById(visitRecord);
            visitStatusSnapshotSyncService.syncFromVisit(visitRecord, now);
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
        recordEvent(ticket, QueueEventTypeEnum.RECALL, QueueStatusEnum.MISSED.name(), QueueStatusEnum.CALLING.name(), ticket.getRoomId(), operatorName, "复呼");
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
        LocalDateTime now = LocalDateTime.now();
        ticket.setCompleteTime(now);
        queueTicketMapper.updateById(ticket);
        VisitRecord visitRecord = requireVisit(ticket.getVisitId());
        visitRecord.setStatus(VisitStatusEnum.COMPLETED.name());
        visitRecordMapper.updateById(visitRecord);
        visitStatusSnapshotSyncService.syncFromVisit(visitRecord, now);
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
        LocalDateTime now = LocalDateTime.now();
        queueTicketMapper.updateById(ticket);
        VisitRecord visitRecord = requireVisit(ticket.getVisitId());
        visitRecord.setStatus(VisitStatusEnum.CANCELLED.name());
        visitRecordMapper.updateById(visitRecord);
        visitStatusSnapshotSyncService.syncFromVisit(visitRecord, now);
        return enrich(ticket);
    }

    @Override
    public QueueRankVO rank(String ticketNo) {
        QueueTicket ticket = requireTicket(ticketNo);
        List<QueueTicket> waitingTickets = listWaitingTickets(ticket.getDeptId());
        return buildRank(ticket, waitingTickets);
    }

    @Override
    public QueueRankVO roomRank(String ticketNo) {
        QueueTicket ticket = requireTicket(ticketNo);
        List<QueueTicket> waitingTickets = ticket.getRoomId() != null
                ? listWaitingTicketsByRoom(ticket.getRoomId())
                : listWaitingTickets(ticket.getDeptId());
        return buildRank(ticket, waitingTickets);
    }

    double calculateQueueScore(QueueTicket ticket, LocalDateTime now) {
        QueuePriorityContext priorityContext = buildPriorityContext(ticket, now, resolveDeptSurgeMode(ticket.getDeptId()));
        return calculateQueueScore(priorityContext);
    }

    private double calculateQueueScore(QueueTicket ticket, LocalDateTime now, boolean surgeMode) {
        return calculateQueueScore(buildPriorityContext(ticket, now, surgeMode));
    }

    private double calculateQueueScore(QueuePriorityContext priorityContext) {
        return -priorityContext.getEffectivePriorityScore() * 1_000_000D
                - priorityContext.getAgingScore() * 1_000D
                + priorityContext.getWaitingMinutes();
    }

    private QueuePriorityContext buildPriorityContext(QueueTicket ticket, LocalDateTime now, boolean surgeMode) {
        long waitingMinutes = resolveWaitingMinutes(ticket, now);
        long agingScore = waitingMinutes * appQueueProperties.getAgingScorePerMinute();
        boolean surgePriorityApplied = surgeMode && isSurgeEligible(ticket);
        int effectivePriorityScore = ticket.getPriorityScore() == null ? 0 : ticket.getPriorityScore();
        if (surgePriorityApplied) {
            effectivePriorityScore += appQueueProperties.getSurgePriorityBonus();
            if (Integer.valueOf(1).equals(ticket.getFastTrack())) {
                effectivePriorityScore += appQueueProperties.getSurgeFastTrackBonus();
            }
        }
        boolean agingBoostApplied = waitingMinutes >= appQueueProperties.getAgingExplainThresholdMinutes();
        return new QueuePriorityContext(waitingMinutes,
                agingScore,
                effectivePriorityScore,
                surgeMode ? "SURGE" : "NORMAL",
                surgePriorityApplied,
                agingBoostApplied,
                buildPriorityReason(ticket, waitingMinutes, surgeMode, surgePriorityApplied, agingBoostApplied, effectivePriorityScore));
    }

    private long resolveWaitingMinutes(QueueTicket ticket, LocalDateTime now) {
        return ticket.getEnqueueTime() == null ? 0 : Math.max(Duration.between(ticket.getEnqueueTime(), now).toMinutes(), 0);
    }

    private boolean resolveDeptSurgeMode(Long deptId) {
        if (deptId == null) {
            return false;
        }
        List<QueueTicket> waitingTickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getDeptId, deptId)
                .eq(QueueTicket::getStatus, QueueStatusEnum.WAITING.name()));
        return resolveDeptSurgeMode(waitingTickets);
    }

    private boolean resolveDeptSurgeMode(List<QueueTicket> waitingTickets) {
        if (waitingTickets == null || waitingTickets.isEmpty()) {
            return false;
        }
        long highPriorityCount = waitingTickets.stream().filter(this::isSurgeEligible).count();
        return waitingTickets.size() >= appQueueProperties.getSurgeWaitingThreshold()
                || highPriorityCount >= appQueueProperties.getSurgeHighPriorityThreshold();
    }

    private boolean isSurgeEligible(QueueTicket ticket) {
        if (ticket == null) {
            return false;
        }
        return Integer.valueOf(1).equals(ticket.getFastTrack())
                || (ticket.getTriageLevel() != null
                && ticket.getTriageLevel() <= appQueueProperties.getSurgeEligibleLevelThreshold());
    }

    private String buildPriorityReason(QueueTicket ticket,
                                       long waitingMinutes,
                                       boolean surgeMode,
                                       boolean surgePriorityApplied,
                                       boolean agingBoostApplied,
                                       int effectivePriorityScore) {
        List<String> reasons = new ArrayList<>();
        if (ticket.getTriageLevel() != null) {
            reasons.add("分诊级别 L" + ticket.getTriageLevel());
        }
        if (ticket.getPriorityScore() != null) {
            reasons.add("基础优先分 " + ticket.getPriorityScore());
        }
        if (Integer.valueOf(1).equals(ticket.getFastTrack())) {
            reasons.add("快速通道");
        }
        if (surgeMode) {
            reasons.add(surgePriorityApplied ? "高峰策略已加权" : "高峰策略监测中");
        }
        if (agingBoostApplied) {
            reasons.add("等待 " + waitingMinutes + " 分钟触发老化补偿");
        }
        reasons.add("当前生效分 " + effectivePriorityScore);
        return String.join("，", reasons);
    }

    /**
     * 基于数据库中的时间字段计算真实已等待时长（分钟）
     */
    private long calculateWaitedMinutes(QueueTicket ticket) {
        LocalDateTime enqueueTime = ticket.getEnqueueTime();
        if (enqueueTime == null) {
            return 0L;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime;
        if (Objects.equals(ticket.getStatus(), QueueStatusEnum.WAITING.name())) {
            // 仍在等待：从入队到当前时间
            endTime = now;
        } else if (ticket.getCallTime() != null) {
            // 已叫号：从入队到叫号时间
            endTime = ticket.getCallTime();
        } else if (ticket.getCompleteTime() != null) {
            // 已完成但无叫号时间：从入队到完成时间
            endTime = ticket.getCompleteTime();
        } else {
            // 其他状态（如取消、过号等）兜底用当前时间
            endTime = now;
        }

        long minutes = Duration.between(enqueueTime, endTime).toMinutes();
        return Math.max(minutes, 0L);
    }

    private QueueTicketVO upsertTicket(Long visitId,
                                       Long assessmentId,
                                       Long roomId,
                                       String operatorName,
                                       String remark,
                                       QueueSourceTypeEnum sourceType,
                                       String sourceRemark,
                                       String lastAdjustReason) {
        VisitRecord visitRecord = requireVisit(visitId);
        TriageAssessment assessment = requireAssessment(assessmentId);
        validateAssessmentBinding(visitRecord, assessment);
        return upsertTicket(visitRecord, assessment, roomId, operatorName, remark, sourceType, sourceRemark, lastAdjustReason);
    }

    private QueueTicketVO upsertTicket(VisitRecord visitRecord,
                                       TriageAssessment assessment,
                                       Long roomId,
                                       String operatorName,
                                       String remark,
                                       QueueSourceTypeEnum sourceType,
                                       String sourceRemark,
                                       String lastAdjustReason) {
        Long deptId = requireDeptId(assessment);
        Long normalizedRoomId = normalizeRoomId(roomId);
        QueueTicket existing = findActiveTicketByVisitId(visitRecord.getId());
        if (normalizedRoomId == null && existing != null && Objects.equals(existing.getStatus(), QueueStatusEnum.WAITING.name())) {
            normalizedRoomId = existing.getRoomId();
        }
        if (existing == null) {
            QueueTicket ticket = buildWaitingTicket(visitRecord, assessment, deptId, normalizedRoomId, sourceType, sourceRemark, lastAdjustReason);
            queueTicketMapper.insert(ticket);
            syncWaitingTicket(ticket);
            recordEvent(ticket, QueueEventTypeEnum.ENQUEUE, null, QueueStatusEnum.WAITING.name(), ticket.getRoomId(), operatorName, remark);
            updateVisitAfterEnqueue(visitRecord, ticket);
            return enrich(ticket);
        }
        if (!Objects.equals(existing.getStatus(), QueueStatusEnum.WAITING.name())) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前就诊已有进行中的排队票据");
        }
        refreshWaitingTicket(existing, assessment, deptId, normalizedRoomId, sourceType, sourceRemark, lastAdjustReason);
        queueTicketMapper.updateById(existing);
        syncWaitingTicket(existing);
        updateVisitAfterEnqueue(visitRecord, existing);
        return enrich(existing);
    }

    private QueueTicket buildWaitingTicket(VisitRecord visitRecord,
                                           TriageAssessment assessment,
                                           Long deptId,
                                           Long roomId,
                                           QueueSourceTypeEnum sourceType,
                                           String sourceRemark,
                                           String lastAdjustReason) {
        QueueTicket ticket = new QueueTicket();
        ticket.setTicketNo(nextTicketNo(deptId));
        ticket.setVisitId(visitRecord.getId());
        ticket.setPatientId(visitRecord.getPatientId());
        ticket.setAssessmentId(assessment.getId());
        ticket.setDeptId(deptId);
        ticket.setRoomId(roomId);
        ticket.setTriageLevel(assessment.getTriageLevel());
        ticket.setPriorityScore(assessment.getPriorityScore());
        ticket.setFastTrack(assessment.getFastTrack());
        ticket.setStatus(QueueStatusEnum.WAITING.name());
        ticket.setRecallCount(0);
        ticket.setSourceType(sourceType == null ? null : sourceType.name());
        ticket.setSourceRemark(sourceRemark);
        ticket.setLastAdjustReason(lastAdjustReason);
        ticket.setEnqueueTime(LocalDateTime.now());
        return ticket;
    }

    private void refreshWaitingTicket(QueueTicket ticket,
                                      TriageAssessment assessment,
                                      Long deptId,
                                      Long roomId,
                                      QueueSourceTypeEnum sourceType,
                                      String sourceRemark,
                                      String lastAdjustReason) {
        Long previousRoomId = ticket.getRoomId();
        Long previousDeptId = ticket.getDeptId();
        if (!Objects.equals(previousDeptId, deptId) || !Objects.equals(previousRoomId, roomId)) {
            removeTicketFromWaitingRedis(ticket.getTicketNo(), previousDeptId, previousRoomId, ticket.getStatus());
        }
        ticket.setAssessmentId(assessment.getId());
        ticket.setDeptId(deptId);
        ticket.setRoomId(roomId);
        ticket.setTriageLevel(assessment.getTriageLevel());
        ticket.setPriorityScore(assessment.getPriorityScore());
        ticket.setFastTrack(assessment.getFastTrack());
        ticket.setSourceType(sourceType == null ? ticket.getSourceType() : sourceType.name());
        ticket.setSourceRemark(sourceRemark);
        if (StringUtils.hasText(lastAdjustReason)) {
            ticket.setLastAdjustReason(lastAdjustReason);
        }
        ticket.setCallTime(null);
        ticket.setCompleteTime(null);
    }

    private void updateVisitAfterEnqueue(VisitRecord visitRecord, QueueTicket ticket) {
        LocalDateTime now = LocalDateTime.now();
        visitRecord.setStatus(VisitStatusEnum.QUEUING.name());
        visitRecord.setCurrentDeptId(ticket.getDeptId());
        visitRecord.setCurrentRoomId(ticket.getRoomId());
        visitRecordMapper.updateById(visitRecord);
        visitStatusSnapshotSyncService.syncFromVisit(visitRecord, now);
    }

    private void validateAssessmentBinding(VisitRecord visitRecord, TriageAssessment assessment) {
        if (!Objects.equals(visitRecord.getId(), assessment.getVisitId())) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), "分诊评估与就诊记录不匹配");
        }
    }

    private void validateVisitStatusForManualCreate(VisitRecord visitRecord) {
        VisitStatusEnum status = VisitStatusEnum.valueOf(visitRecord.getStatus());
        if (status == VisitStatusEnum.COMPLETED || status == VisitStatusEnum.CANCELLED) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前就诊状态不可执行异常补录/管理员修复");
        }
        if (status != VisitStatusEnum.ARRIVED && status != VisitStatusEnum.TRIAGED) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "仅已到诊或已分诊记录允许异常补录/管理员修复");
        }
    }

    private void validateNoManualCreateConflict(QueueTicket existing) {
        if (existing == null) {
            return;
        }
        throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前就诊已有进行中的排队票据，请勿重复建票");
    }

    private Long requireDeptId(TriageAssessment assessment) {
        if (assessment.getRecommendDeptId() == null) {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "分诊结果缺少推荐科室，无法入队");
        }
        return assessment.getRecommendDeptId();
    }

    private Long pickRoomIdForKiosk(TriageAssessment assessment) {
        Long deptId = assessment == null ? null : assessment.getRecommendDeptId();
        if (deptId == null) {
            return null;
        }
        List<ClinicRoom> rooms = clinicRoomMapper.selectList(new LambdaQueryWrapper<ClinicRoom>()
                .eq(ClinicRoom::getDeptId, deptId)
                .eq(ClinicRoom::getEnabled, 1)
                .orderByAsc(ClinicRoom::getId));
        if (rooms.isEmpty()) {
            return null;
        }
        if (rooms.size() == 1) {
            return rooms.get(0).getId();
        }
        Map<Long, RoomLoadSnapshot> roomLoadSnapshots = loadRoomLoadSnapshots(rooms);
        Long priorityRoomId = resolvePriorityRoomIdForKiosk(deptId, rooms);
        boolean severeCase = isPriorityKioskCase(assessment);
        return rooms.stream()
                .min(Comparator
                        .comparingInt((ClinicRoom room) -> calculateKioskRoomScore(
                                roomLoadSnapshots.get(room.getId()),
                                severeCase,
                                Objects.equals(room.getId(), priorityRoomId)))
                        .thenComparing(room -> {
                            RoomLoadSnapshot snapshot = roomLoadSnapshots.get(room.getId());
                            return snapshot == null ? null : snapshot.getLatestAssignedTime();
                        }, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingInt(room -> {
                            RoomLoadSnapshot snapshot = roomLoadSnapshots.get(room.getId());
                            return snapshot == null ? 0 : snapshot.getTodayAssignedCount();
                        })
                        .thenComparingLong(ClinicRoom::getId))
                .map(ClinicRoom::getId)
                .orElse(null);
    }

    private Map<Long, RoomLoadSnapshot> loadRoomLoadSnapshots(List<ClinicRoom> rooms) {
        List<Long> roomIds = rooms.stream()
                .map(ClinicRoom::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, RoomLoadSnapshot> snapshots = roomIds.stream()
                .collect(Collectors.toMap(roomId -> roomId, roomId -> new RoomLoadSnapshot(), (left, right) -> left));
        if (roomIds.isEmpty()) {
            return snapshots;
        }

        List<QueueTicket> activeTickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                .in(QueueTicket::getRoomId, roomIds)
                .in(QueueTicket::getStatus,
                        QueueStatusEnum.WAITING.name(),
                        QueueStatusEnum.CALLING.name(),
                        QueueStatusEnum.MISSED.name()));
        if (activeTickets != null) {
            activeTickets.forEach(ticket -> {
                RoomLoadSnapshot snapshot = snapshots.get(ticket.getRoomId());
                if (snapshot != null) {
                    snapshot.recordActiveStatus(ticket.getStatus());
                }
            });
        }

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        List<QueueTicket> todayTickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                .in(QueueTicket::getRoomId, roomIds)
                .ge(QueueTicket::getEnqueueTime, dayStart));
        if (todayTickets != null) {
            todayTickets.forEach(ticket -> {
                RoomLoadSnapshot snapshot = snapshots.get(ticket.getRoomId());
                if (snapshot != null) {
                    snapshot.recordAssignment(ticket.getEnqueueTime() == null ? ticket.getCreatedTime() : ticket.getEnqueueTime());
                }
            });
        }
        return snapshots;
    }

    private Long resolvePriorityRoomIdForKiosk(Long deptId, List<ClinicRoom> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return null;
        }
        Map<Long, Long> configuredRoomMap = appQueueProperties.getSeverePriorityRoomByDept();
        if (configuredRoomMap != null) {
            Long configuredRoomId = configuredRoomMap.get(deptId);
            if (configuredRoomId != null && rooms.stream().anyMatch(room -> Objects.equals(room.getId(), configuredRoomId))) {
                return configuredRoomId;
            }
        }
        return rooms.stream()
                .map(ClinicRoom::getId)
                .filter(Objects::nonNull)
                .min(Long::compareTo)
                .orElse(null);
    }

    private boolean isPriorityKioskCase(TriageAssessment assessment) {
        if (assessment == null) {
            return false;
        }
        int severeLevelThreshold = defaultInt(appQueueProperties.getKioskSevereLevelThreshold(), 2);
        if (assessment.getTriageLevel() != null && assessment.getTriageLevel() <= severeLevelThreshold) {
            return true;
        }
        if (assessment.getAiSuggestedLevel() != null && assessment.getAiSuggestedLevel() <= severeLevelThreshold) {
            return true;
        }
        if (Integer.valueOf(1).equals(assessment.getFastTrack())) {
            return true;
        }
        if (!StringUtils.hasText(assessment.getAiRiskLevel())) {
            return false;
        }
        String normalizedRiskLevel = assessment.getAiRiskLevel().trim().toUpperCase(Locale.ROOT);
        return Objects.equals(normalizedRiskLevel, "HIGH") || Objects.equals(normalizedRiskLevel, "CRITICAL");
    }

    private int calculateKioskRoomScore(RoomLoadSnapshot snapshot, boolean severeCase, boolean priorityRoom) {
        RoomLoadSnapshot loadSnapshot = snapshot == null ? new RoomLoadSnapshot() : snapshot;
        int score = loadSnapshot.getWaitingCount() * defaultInt(appQueueProperties.getKioskWaitingWeight(), 100)
                + loadSnapshot.getCallingCount() * defaultInt(appQueueProperties.getKioskCallingWeight(), 130)
                + loadSnapshot.getMissedCount() * defaultInt(appQueueProperties.getKioskMissedWeight(), 90)
                + loadSnapshot.getTodayAssignedCount() * defaultInt(appQueueProperties.getKioskDailyAssignmentWeight(), 12);
        if (priorityRoom) {
            score += severeCase
                    ? -defaultInt(appQueueProperties.getKioskPriorityRoomBonus(), 180)
                    : defaultInt(appQueueProperties.getKioskPriorityRoomReservePenalty(), 120);
        }
        return score;
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Long normalizeRoomId(Long roomId) {
        return roomId != null && roomId > 0 ? roomId : null;
    }

    private QueueTicket findActiveTicketByVisitId(Long visitId) {
        return queueTicketMapper.selectOne(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getVisitId, visitId)
                .in(QueueTicket::getStatus,
                        QueueStatusEnum.WAITING.name(),
                        QueueStatusEnum.CALLING.name(),
                        QueueStatusEnum.MISSED.name())
                .orderByDesc(QueueTicket::getUpdatedTime)
                .last("limit 1"));
    }

    private QueueTicket findLatestTicketByVisitId(Long visitId) {
        return queueTicketMapper.selectOne(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getVisitId, visitId)
                .orderByDesc(QueueTicket::getUpdatedTime)
                .last("limit 1"));
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
        recordEvent(ticket, eventType, fromStatus, targetStatus.name(), roomId, operatorName, remark);
    }

    private void syncWaitingTicket(QueueTicket ticket) {
        QueuePriorityContext priorityContext = buildPriorityContext(ticket, LocalDateTime.now(), resolveDeptSurgeMode(ticket.getDeptId()));
        String deptKey = String.format(RedisKeyConstants.QUEUE_DEPT_ACTIVE, ticket.getDeptId());
        double queueScore = -priorityContext.getEffectivePriorityScore() * 1_000_000D
                - priorityContext.getAgingScore() * 1_000D
                + priorityContext.getWaitingMinutes();
        redisTemplate.opsForZSet().add(deptKey, ticket.getTicketNo(), queueScore);
        if (ticket.getRoomId() != null) {
            String roomKey = String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, ticket.getRoomId());
            redisTemplate.opsForZSet().add(roomKey, ticket.getTicketNo(), queueScore);
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
                STRING_REDIS_SERIALIZER,
                STRING_REDIS_SERIALIZER,
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
        String normalizedClaim = normalizeRedisTicketNo(claim);
        int splitIndex = normalizedClaim.indexOf(':');
        if (splitIndex < 0) {
            return QueueClaimResult.builder().ticketNo(normalizedClaim).build();
        }
        return QueueClaimResult.builder()
                .source(normalizedClaim.substring(0, splitIndex))
                .ticketNo(normalizeRedisTicketNo(normalizedClaim.substring(splitIndex + 1)))
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

    private boolean rebuildWaitingQueueIndex(Long roomId, Long deptId) {
        if (deptId != null) {
            redisTemplate.delete(String.format(RedisKeyConstants.QUEUE_DEPT_ACTIVE, deptId));
        }
        if (roomId != null) {
            redisTemplate.delete(String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, roomId));
        }
        List<QueueTicket> waitingTickets = deptId != null ? listWaitingTickets(deptId) : listWaitingTicketsByRoom(roomId);
        if (waitingTickets.isEmpty()) {
            return false;
        }
        waitingTickets.forEach(this::syncWaitingTicket);
        return true;
    }

    private void evictGhostTicketFromQueueIndex(String ticketNo, Long deptId, Long roomId) {
        if (!StringUtils.hasText(ticketNo)) {
            return;
        }
        if (deptId != null) {
            redisTemplate.opsForZSet().remove(String.format(RedisKeyConstants.QUEUE_DEPT_ACTIVE, deptId), ticketNo);
        }
        if (roomId != null) {
            redisTemplate.opsForZSet().remove(String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, roomId), ticketNo);
        }
        redisTemplate.delete(String.format(RedisKeyConstants.QUEUE_TICKET, ticketNo));
    }

    private void clearStaleCallingTicket(Long roomId) {
        if (roomId == null) {
            return;
        }
        String callingKey = String.format(RedisKeyConstants.QUEUE_CALLING, roomId);
        Object current = redisTemplate.opsForValue().get(callingKey);
        if (current == null) {
            return;
        }
        String ticketNo = normalizeRedisTicketNo(String.valueOf(current));
        QueueTicket ticket = getTicketOrNull(ticketNo);
        if (ticket != null
                && Objects.equals(ticket.getStatus(), QueueStatusEnum.CALLING.name())
                && Objects.equals(ticket.getRoomId(), roomId)) {
            return;
        }
        redisTemplate.delete(callingKey);
    }

    private void clearCallingTicket(Long roomId, String ticketNo) {
        String callingKey = String.format(RedisKeyConstants.QUEUE_CALLING, roomId);
        Object current = redisTemplate.opsForValue().get(callingKey);
        String normalizedCurrent = current == null ? null : normalizeRedisTicketNo(String.valueOf(current));
        String normalizedTicketNo = normalizeRedisTicketNo(ticketNo);
        if (normalizedCurrent != null && Objects.equals(normalizedTicketNo, normalizedCurrent)) {
            redisTemplate.delete(callingKey);
        }
    }

    private void refreshCallingTicket(Long roomId, String ticketNo) {
        redisTemplate.opsForValue().set(String.format(RedisKeyConstants.QUEUE_CALLING, roomId), ticketNo,
                appQueueProperties.getCallingTtlSeconds(), TimeUnit.SECONDS);
    }

    private List<QueueTicket> listWaitingTickets(Long deptId) {
        if (deptId == null) {
            return listWaitingTicketsAll();
        }
        return listWaitingTicketsByDept(deptId);
    }

    private List<QueueTicket> listWaitingTicketsByDept(Long deptId) {
        List<QueueTicket> waitingTickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getDeptId, deptId)
                .eq(QueueTicket::getStatus, QueueStatusEnum.WAITING.name()));
        if (waitingTickets == null || waitingTickets.isEmpty()) {
            return List.of();
        }
        boolean surgeMode = resolveDeptSurgeMode(waitingTickets);
        LocalDateTime now = LocalDateTime.now();
        return waitingTickets.stream()
                .sorted((a, b) -> Double.compare(
                        calculateQueueScore(a, now, surgeMode),
                        calculateQueueScore(b, now, surgeMode)))
                .toList();
    }

    private List<QueueTicket> listWaitingTicketsByRoom(Long roomId) {
        List<QueueTicket> waitingTickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getRoomId, roomId)
                .eq(QueueTicket::getStatus, QueueStatusEnum.WAITING.name()));
        if (waitingTickets == null || waitingTickets.isEmpty()) {
            return List.of();
        }
        boolean surgeMode = resolveDeptSurgeMode(waitingTickets);
        LocalDateTime now = LocalDateTime.now();
        return waitingTickets.stream()
                .sorted((a, b) -> Double.compare(
                        calculateQueueScore(a, now, surgeMode),
                        calculateQueueScore(b, now, surgeMode)))
                .toList();
    }

    private List<QueueTicket> listWaitingTicketsAll() {
        List<QueueTicket> waitingTickets = queueTicketMapper.selectList(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getStatus, QueueStatusEnum.WAITING.name()));
        if (waitingTickets == null || waitingTickets.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return waitingTickets.stream()
                .sorted((a, b) -> Double.compare(calculateQueueScore(a, now), calculateQueueScore(b, now)))
                .toList();
    }

    private List<QueueTicket> listCallingTickets(Long deptId) {
        return listCallingTickets(deptId, null);
    }

    private List<QueueTicket> listCallingTickets(Long deptId, Long roomId) {
        LambdaQueryWrapper<QueueTicket> wrapper = new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getStatus, QueueStatusEnum.CALLING.name());
        wrapper.eq(roomId != null, QueueTicket::getRoomId, roomId);
        wrapper.eq(roomId == null && deptId != null, QueueTicket::getDeptId, deptId);
        return queueTicketMapper.selectList(wrapper)
                .stream()
                .sorted((a, b) -> {
                    LocalDateTime left = a.getCallTime();
                    LocalDateTime right = b.getCallTime();
                    if (left == null && right == null) {
                        return 0;
                    }
                    if (left == null) {
                        return 1;
                    }
                    if (right == null) {
                        return -1;
                    }
                    return right.compareTo(left);
                })
                .toList();
    }

    private String nextTicketNo(Long deptId) {
        String date = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = String.format(RedisKeyConstants.QUEUE_SEQ, date, deptId);
        // 为了避免 Redis 计数丢失或重置导致和数据库中已有 ticket_no 冲突，这里做一次 DB 兜底检查并有限次重试
        int maxRetry = 5;
        for (int i = 0; i < maxRetry; i++) {
            Long seq = redisTemplate.opsForValue().increment(key);
            // 确保当天的序列 key 仍然有过期时间
            redisTemplate.expire(key, 1, TimeUnit.DAYS);
            long sequence = (seq == null ? 1L : seq);
            String candidate = date + "-" + deptId + "-" + String.format("%04d", sequence);

            Long count = queueTicketMapper.selectCount(new LambdaQueryWrapper<QueueTicket>()
                    .eq(QueueTicket::getTicketNo, candidate));
            if (count == null || count == 0L) {
                return candidate;
            }
            // 如果已存在，继续下一轮重试，使用下一个序号
        }
        // 理论上不太可能触发，触发时说明当天号码已经异常紧张，给出明确错误提示
        throw new ServiceException(ErrorCodeEnum.SYSTEM_ERROR.getCode(), "今日排队号生成失败，请稍后重试");
    }

    private void recordEvent(QueueTicket ticket,
                             QueueEventTypeEnum eventType,
                             String fromStatus,
                             String toStatus,
                             Long roomId,
                             String operatorName,
                             String remark) {
        QueueEventLog eventLog = new QueueEventLog();
        eventLog.setTicketNo(ticket.getTicketNo());
        eventLog.setVisitId(ticket.getVisitId());
        eventLog.setPatientId(ticket.getPatientId());
        eventLog.setDeptId(ticket.getDeptId());
        eventLog.setEventType(eventType.name());
        eventLog.setFromStatus(fromStatus);
        eventLog.setToStatus(toStatus);
        eventLog.setRoomId(roomId);
        eventLog.setOperatorName(operatorName);
        eventLog.setSourceType(ticket.getSourceType());
        eventLog.setSourceRemark(ticket.getSourceRemark());
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
        String normalizedTicketNo = normalizeRedisTicketNo(ticketNo);
        if (!StringUtils.hasText(normalizedTicketNo)) {
            return null;
        }
        return queueTicketMapper.selectOne(new LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getTicketNo, normalizedTicketNo)
                .last("limit 1"));
    }

    private String normalizeRedisTicketNo(String ticketNo) {
        if (!StringUtils.hasText(ticketNo)) {
            return ticketNo;
        }
        String normalized = ticketNo.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
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
        Long mappedDeptId = findRoomDeptId(roomId);
        if (mappedDeptId != null) {
            return mappedDeptId;
        }
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(String.format(RedisKeyConstants.QUEUE_ROOM_ACTIVE, roomId), 0, 0);
        if (tuples != null && !tuples.isEmpty()) {
            Object value = tuples.iterator().next().getValue();
            if (value != null) {
                QueueTicket ticket = requireTicket(normalizeRedisTicketNo(String.valueOf(value)));
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

    private Long findRoomDeptId(Long roomId) {
        ClinicRoom room = clinicRoomMapper.selectOne(new LambdaQueryWrapper<ClinicRoom>()
                .eq(ClinicRoom::getId, roomId)
                .eq(ClinicRoom::getEnabled, 1)
                .last("limit 1"));
        return room == null ? null : room.getDeptId();
    }

    private QueueTicketVO enrich(QueueTicket ticket) {
        List<QueueTicket> waitingTickets = listWaitingTickets(ticket.getDeptId());
        Map<Long, String> patientNames = loadPatientNames(List.of(ticket));
        Map<Long, String> patientNos = loadPatientNos(List.of(ticket));
        Map<Long, String> deptNames = loadDeptNames(List.of(ticket));
        Map<Long, ClinicRoom> roomIndex = loadRooms(List.of(ticket));
        Map<Long, TriageAssessment> assessmentIndex = loadAssessments(List.of(ticket));
        Map<Long, List<QueueTicket>> waitingTicketIndex = buildWaitingTicketIndex(waitingTickets);
        Map<Long, List<QueueTicket>> roomWaitingTicketIndex = buildRoomWaitingTicketIndex(waitingTickets);
        return enrich(ticket, waitingTicketIndex, roomWaitingTicketIndex, patientNames, patientNos, deptNames, roomIndex, assessmentIndex);
    }

    private List<QueueTicketVO> enrichTickets(List<QueueTicket> tickets,
                                              Map<Long, List<QueueTicket>> waitingTicketIndex,
                                              Map<Long, List<QueueTicket>> roomWaitingTicketIndex,
                                              Map<Long, String> patientNames,
                                              Map<Long, String> patientNos,
                                              Map<Long, String> deptNames,
                                              Map<Long, ClinicRoom> roomIndex) {
        Map<Long, TriageAssessment> assessmentIndex = loadAssessments(tickets);
        return tickets.stream()
                .map(ticket -> enrich(ticket, waitingTicketIndex, roomWaitingTicketIndex, patientNames, patientNos, deptNames, roomIndex, assessmentIndex))
                .toList();
    }

    private QueueTicketVO enrich(QueueTicket ticket,
                                 Map<Long, List<QueueTicket>> waitingTicketIndex,
                                 Map<Long, List<QueueTicket>> roomWaitingTicketIndex,
                                 Map<Long, String> patientNames,
                                 Map<Long, String> patientNos,
                                 Map<Long, String> deptNames,
                                 Map<Long, ClinicRoom> roomIndex,
                                 Map<Long, TriageAssessment> assessmentIndex) {
        QueueTicketVO vo = new QueueTicketVO();
        BeanUtils.copyProperties(ticket, vo);
        List<QueueTicket> deptWaitingTickets = waitingTicketIndex.getOrDefault(ticket.getDeptId(), List.of());
        QueueRankVO rankVO = buildRank(ticket, deptWaitingTickets);
        QueuePriorityContext priorityContext = buildPriorityContext(ticket, LocalDateTime.now(), resolveDeptSurgeMode(deptWaitingTickets));
        vo.setRank(rankVO.getRank());
        vo.setWaitingCount(rankVO.getWaitingCount());
        vo.setEstimatedWaitMinutes(rankVO.getEstimatedWaitMinutes());
        vo.setWaitedMinutes(calculateWaitedMinutes(ticket));
        vo.setPriorityReason(priorityContext.getPriorityReason());
        vo.setQueueStrategyMode(priorityContext.getQueueStrategyMode());
        vo.setSurgePriorityApplied(priorityContext.getSurgePriorityApplied());
        vo.setAgingBoostApplied(priorityContext.getAgingBoostApplied());
        vo.setPatientName(patientNames.get(ticket.getPatientId()));
        vo.setPatientNo(patientNos.get(ticket.getPatientId()));
        vo.setDeptName(deptNames.get(ticket.getDeptId()));
        Long roomId = ticket.getRoomId();
        List<QueueTicket> roomWaitingTickets = roomId == null ? List.of() : roomWaitingTicketIndex.getOrDefault(roomId, List.of());
        boolean waitingForConsultation = isWaitingForConsultation(ticket, roomWaitingTickets);
        String displayStatus = resolveDisplayStatus(ticket.getStatus(), waitingForConsultation);
        vo.setWaitingForConsultation(waitingForConsultation);
        vo.setDisplayStatus(displayStatus);
        vo.setDisplayStatusText(resolveDisplayStatusText(displayStatus));
        ClinicRoom room = roomId == null ? null : roomIndex.get(roomId);
        if (room != null) {
            vo.setRoomName(room.getRoomName());
            vo.setDoctorName(room.getDoctorName());
        }
        TriageAssessment assessment = ticket.getAssessmentId() == null ? null : assessmentIndex.get(ticket.getAssessmentId());
        applyAiAssessment(vo, assessment);
        return vo;
    }

    private Map<Long, TriageAssessment> loadAssessments(List<QueueTicket>... groups) {
        List<Long> assessmentIds = java.util.Arrays.stream(groups)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(QueueTicket::getAssessmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (assessmentIds.isEmpty()) {
            return Map.of();
        }
        return triageAssessmentMapper.selectBatchIds(assessmentIds).stream()
                .collect(Collectors.toMap(TriageAssessment::getId, assessment -> assessment, (left, right) -> left));
    }

    private void applyAiAssessment(QueueTicketVO vo, TriageAssessment assessment) {
        if (assessment == null) {
            return;
        }
        vo.setChiefComplaint(assessment.getSymptomTags());
        vo.setAiAdvice(assessment.getAiAdvice());
        vo.setAiSuggestedLevel(assessment.getAiSuggestedLevel());
        vo.setAiRiskLevel(assessment.getAiRiskLevel());
        vo.setAiNeedManualReview(assessment.getAiNeedManualReview());
        vo.setAiPriorityAdvice(buildAiPriorityAdvice(assessment));
    }

    private String buildAiPriorityAdvice(TriageAssessment assessment) {
        if (assessment == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (assessment.getAiSuggestedLevel() != null) {
            parts.add("AI建议 " + assessment.getAiSuggestedLevel() + "级");
        }
        if (StringUtils.hasText(assessment.getAiRiskLevel())) {
            parts.add("风险 " + formatAiRiskLevel(assessment.getAiRiskLevel()));
        }
        if (Boolean.TRUE.equals(assessment.getAiNeedManualReview())) {
            parts.add("建议人工复核");
        }
        if (parts.isEmpty() && StringUtils.hasText(assessment.getAiAdvice())) {
            return assessment.getAiAdvice();
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    private String formatAiRiskLevel(String riskLevel) {
        return switch (riskLevel) {
            case "CRITICAL" -> "危急";
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            default -> riskLevel;
        };
    }

    private boolean isWaitingForConsultation(QueueTicket ticket, List<QueueTicket> roomWaitingTickets) {
        return Objects.equals(ticket.getStatus(), QueueStatusEnum.WAITING.name())
                && ticket.getRoomId() != null
                && !roomWaitingTickets.isEmpty()
                && Objects.equals(roomWaitingTickets.get(0).getTicketNo(), ticket.getTicketNo());
    }

    private String resolveDisplayStatus(String status, boolean waitingForConsultation) {
        if (Objects.equals(status, QueueStatusEnum.WAITING.name())) {
            return waitingForConsultation ? "WAITING_FOR_CONSULTATION" : "QUEUEING";
        }
        return status;
    }

    private String resolveDisplayStatusText(String displayStatus) {
        if (!StringUtils.hasText(displayStatus)) {
            return "暂无状态";
        }
        return switch (displayStatus) {
            case "WAITING_FOR_CONSULTATION" -> "候诊中";
            case "QUEUEING", "WAITING" -> "排队中";
            case "CALLING" -> "叫号中";
            case "MISSED" -> "已过号";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> displayStatus;
        };
    }

    private QueueRankVO buildRank(QueueTicket ticket, List<QueueTicket> waitingTickets) {
        String ticketNo = ticket.getTicketNo();
        long rankIndex = waitingTickets.stream().map(QueueTicket::getTicketNo).toList().indexOf(ticketNo);
        long normalizedRank = Objects.equals(ticket.getStatus(), QueueStatusEnum.WAITING.name()) && rankIndex >= 0 ? rankIndex + 1 : 0;
        long waitingCount = normalizedRank == 0 ? 0 : rankIndex;
        return QueueRankVO.builder()
                .ticketNo(ticketNo)
                .status(ticket.getStatus())
                .rank(normalizedRank)
                .waitingCount(waitingCount)
                .estimatedWaitMinutes(normalizedRank == 0 ? 0 : waitingCount * appQueueProperties.getEstimateMinutesPerPerson())
                .build();
    }

    private Map<Long, String> loadPatientNames(List<QueueTicket>... groups) {
        List<Long> patientIds = java.util.Arrays.stream(groups)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(QueueTicket::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (patientIds.isEmpty()) {
            return Map.of();
        }
        return patientInfoMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(PatientInfo::getId, PatientInfo::getPatientName, (left, right) -> left));
    }

    /**
     * 加载患者业务编号 patientNo（对人友好的短编号），用于前端展示，避免直接暴露内部自增 ID
     */
    private Map<Long, String> loadPatientNos(List<QueueTicket>... groups) {
        List<Long> patientIds = java.util.Arrays.stream(groups)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(QueueTicket::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (patientIds.isEmpty()) {
            return Map.of();
        }
        return patientInfoMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(PatientInfo::getId, PatientInfo::getPatientNo, (left, right) -> left));
    }

    private Map<Long, String> loadDeptNames(List<QueueTicket>... groups) {
        List<Long> deptIds = java.util.Arrays.stream(groups)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(QueueTicket::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        return clinicDeptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(ClinicDept::getId, ClinicDept::getDeptName, (left, right) -> left));
    }

    private Map<Long, ClinicRoom> loadRooms(List<QueueTicket>... groups) {
        List<Long> roomIds = java.util.Arrays.stream(groups)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(QueueTicket::getRoomId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        return clinicRoomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(ClinicRoom::getId, room -> room, (left, right) -> left));
    }

    private Map<Long, List<QueueTicket>> buildWaitingTicketIndex(List<QueueTicket> waitingTickets) {
        return waitingTickets.stream()
                .collect(Collectors.groupingBy(QueueTicket::getDeptId, Collectors.toList()));
    }

    private Map<Long, List<QueueTicket>> buildRoomWaitingTicketIndex(List<QueueTicket> waitingTickets) {
        return waitingTickets.stream()
                .filter(ticket -> ticket.getRoomId() != null)
                .collect(Collectors.groupingBy(QueueTicket::getRoomId, Collectors.toList()));
    }

    private static final class RoomLoadSnapshot {

        private int waitingCount;
        private int callingCount;
        private int missedCount;
        private int todayAssignedCount;
        private LocalDateTime latestAssignedTime;

        private void recordActiveStatus(String status) {
            if (Objects.equals(status, QueueStatusEnum.WAITING.name())) {
                waitingCount++;
                return;
            }
            if (Objects.equals(status, QueueStatusEnum.CALLING.name())) {
                callingCount++;
                return;
            }
            if (Objects.equals(status, QueueStatusEnum.MISSED.name())) {
                missedCount++;
            }
        }

        private void recordAssignment(LocalDateTime assignedTime) {
            todayAssignedCount++;
            if (assignedTime != null && (latestAssignedTime == null || assignedTime.isAfter(latestAssignedTime))) {
                latestAssignedTime = assignedTime;
            }
        }

        private int getWaitingCount() {
            return waitingCount;
        }

        private int getCallingCount() {
            return callingCount;
        }

        private int getMissedCount() {
            return missedCount;
        }

        private int getTodayAssignedCount() {
            return todayAssignedCount;
        }

        private LocalDateTime getLatestAssignedTime() {
            return latestAssignedTime;
        }
    }

    private static final class QueuePriorityContext {

        private final long waitingMinutes;
        private final long agingScore;
        private final int effectivePriorityScore;
        private final String queueStrategyMode;
        private final boolean surgePriorityApplied;
        private final boolean agingBoostApplied;
        private final String priorityReason;

        private QueuePriorityContext(long waitingMinutes,
                                     long agingScore,
                                     int effectivePriorityScore,
                                     String queueStrategyMode,
                                     boolean surgePriorityApplied,
                                     boolean agingBoostApplied,
                                     String priorityReason) {
            this.waitingMinutes = waitingMinutes;
            this.agingScore = agingScore;
            this.effectivePriorityScore = effectivePriorityScore;
            this.queueStrategyMode = queueStrategyMode;
            this.surgePriorityApplied = surgePriorityApplied;
            this.agingBoostApplied = agingBoostApplied;
            this.priorityReason = priorityReason;
        }

        private long getWaitingMinutes() {
            return waitingMinutes;
        }

        private long getAgingScore() {
            return agingScore;
        }

        private int getEffectivePriorityScore() {
            return effectivePriorityScore;
        }

        private String getQueueStrategyMode() {
            return queueStrategyMode;
        }

        private boolean getSurgePriorityApplied() {
            return surgePriorityApplied;
        }

        private boolean getAgingBoostApplied() {
            return agingBoostApplied;
        }

        private String getPriorityReason() {
            return priorityReason;
        }
    }
}
