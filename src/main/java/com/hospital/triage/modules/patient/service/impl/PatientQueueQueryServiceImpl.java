package com.hospital.triage.modules.patient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.common.enums.QueueStatusEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.entity.po.ClinicRoom;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.clinic.mapper.ClinicRoomMapper;
import com.hospital.triage.modules.patient.entity.dto.PatientQueueQueryDTO;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueNextStepVO;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueViewVO;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.patient.service.PatientQueueQueryService;
import com.hospital.triage.modules.queue.entity.vo.QueueRankVO;
import com.hospital.triage.modules.queue.entity.vo.QueueTicketVO;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class PatientQueueQueryServiceImpl implements PatientQueueQueryService {

    private static final String QUERY_FAILED_MESSAGE = "患者信息或校验信息不正确";

    private final PatientInfoMapper patientInfoMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final QueueDispatchService queueDispatchService;
    private final ClinicDeptMapper clinicDeptMapper;
    private final ClinicRoomMapper clinicRoomMapper;
    private final TriageAssessmentMapper triageAssessmentMapper;

    public PatientQueueQueryServiceImpl(PatientInfoMapper patientInfoMapper,
                                        VisitRecordMapper visitRecordMapper,
                                        QueueDispatchService queueDispatchService,
                                        ClinicDeptMapper clinicDeptMapper,
                                        ClinicRoomMapper clinicRoomMapper,
                                        TriageAssessmentMapper triageAssessmentMapper) {
        this.patientInfoMapper = patientInfoMapper;
        this.visitRecordMapper = visitRecordMapper;
        this.queueDispatchService = queueDispatchService;
        this.clinicDeptMapper = clinicDeptMapper;
        this.clinicRoomMapper = clinicRoomMapper;
        this.triageAssessmentMapper = triageAssessmentMapper;
    }

    @Override
    public PatientQueueViewVO query(PatientQueueQueryDTO dto) {
        String phoneSuffix = normalizePhoneSuffix(dto.getPhoneSuffix(), QUERY_FAILED_MESSAGE);
        PatientInfo patientInfo = resolvePatientInfo(dto.getPatientNo(), dto.getPatientName(), phoneSuffix, QUERY_FAILED_MESSAGE);

        VisitRecord visitRecord = resolveCurrentVisit(patientInfo);
        QueueTicketVO ticket = visitRecord == null ? null : queueDispatchService.getLatestTicketByVisitId(visitRecord.getId());
        TriageAssessment assessment = resolveLatestAssessment(visitRecord, ticket);
        QueueRankVO rank = shouldLoadRank(ticket) ? queueDispatchService.rank(ticket.getTicketNo()) : null;
        QueueRankVO roomRank = shouldLoadRoomRank(ticket) ? queueDispatchService.roomRank(ticket.getTicketNo()) : null;

        return buildView(patientInfo, visitRecord, ticket, assessment, rank, roomRank);
    }

    private PatientInfo resolvePatientInfo(String patientNo, String patientName, String phoneSuffix, String errorMessage) {
        String normalizedPatientNo = normalizeIdentifier(patientNo);
        String normalizedPatientName = normalizeIdentifier(patientName);
        boolean hasPatientNo = StringUtils.hasText(normalizedPatientNo);
        boolean hasPatientName = StringUtils.hasText(normalizedPatientName);
        if (hasPatientNo == hasPatientName) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), errorMessage);
        }
        if (hasPatientNo) {
            return resolveByPatientNo(normalizedPatientNo, phoneSuffix, errorMessage);
        }
        return resolveByPatientName(normalizedPatientName, phoneSuffix, errorMessage);
    }

    private PatientInfo resolveByPatientNo(String patientNo, String phoneSuffix, String errorMessage) {
        PatientInfo patientInfo = patientInfoMapper.selectOne(new LambdaQueryWrapper<PatientInfo>()
                .eq(PatientInfo::getPatientNo, patientNo)
                .last("limit 1"));
        validatePatientCredential(patientInfo, phoneSuffix, errorMessage);
        return patientInfo;
    }

    private PatientInfo resolveByPatientName(String patientName, String phoneSuffix, String errorMessage) {
        List<PatientInfo> candidates = patientInfoMapper.selectList(new LambdaQueryWrapper<PatientInfo>()
                .eq(PatientInfo::getPatientName, patientName));
        List<PatientInfo> matched = candidates.stream()
                .filter(candidate -> hasMatchingPhoneSuffix(candidate, phoneSuffix))
                .toList();
        if (matched.size() != 1) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), errorMessage);
        }
        return matched.get(0);
    }

    private void validatePatientCredential(PatientInfo patientInfo, String phoneSuffix, String errorMessage) {
        if (patientInfo == null || !hasMatchingPhoneSuffix(patientInfo, phoneSuffix)) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), errorMessage);
        }
    }

    private boolean hasMatchingPhoneSuffix(PatientInfo patientInfo, String phoneSuffix) {
        if (patientInfo == null) {
            return false;
        }
        String phone = patientInfo.getPhone();
        return StringUtils.hasText(phone)
                && phone.length() >= 4
                && Objects.equals(phone.substring(phone.length() - 4), phoneSuffix);
    }

    private String normalizePhoneSuffix(String phoneSuffix, String errorMessage) {
        String normalized = normalizeIdentifier(phoneSuffix);
        if (!StringUtils.hasText(normalized) || !normalized.matches("\\d{4}")) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), errorMessage);
        }
        return normalized;
    }

    private String normalizeIdentifier(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private VisitRecord resolveCurrentVisit(PatientInfo patientInfo) {
        if (patientInfo.getCurrentVisitId() != null) {
            VisitRecord currentVisit = visitRecordMapper.selectById(patientInfo.getCurrentVisitId());
            if (currentVisit != null && Objects.equals(currentVisit.getPatientId(), patientInfo.getId())) {
                return currentVisit;
            }
        }

        List<VisitRecord> visits = visitRecordMapper.selectList(new LambdaQueryWrapper<VisitRecord>()
                .eq(VisitRecord::getPatientId, patientInfo.getId())
                .notIn(VisitRecord::getStatus, VisitStatusEnum.COMPLETED.name(), VisitStatusEnum.CANCELLED.name())
                .orderByDesc(VisitRecord::getUpdatedTime, VisitRecord::getId));
        if (!visits.isEmpty()) {
            return visits.get(0);
        }

        return visitRecordMapper.selectOne(new LambdaQueryWrapper<VisitRecord>()
                .eq(VisitRecord::getPatientId, patientInfo.getId())
                .orderByDesc(VisitRecord::getUpdatedTime, VisitRecord::getId)
                .last("limit 1"));
    }

    private boolean shouldLoadRank(QueueTicketVO ticket) {
        return ticket != null && Objects.equals(ticket.getStatus(), QueueStatusEnum.WAITING.name());
    }

    private boolean shouldLoadRoomRank(QueueTicketVO ticket) {
        return shouldLoadRank(ticket)
                && ticket.getRoomId() != null
                && ticket.getWaitingForConsultation() == null;
    }

    private PatientQueueViewVO buildView(PatientInfo patientInfo,
                                         VisitRecord visitRecord,
                                         QueueTicketVO ticket,
                                         TriageAssessment assessment,
                                         QueueRankVO rank,
                                         QueueRankVO roomRank) {
        PatientQueueViewVO view = new PatientQueueViewVO();
        view.setPatientId(patientInfo.getId());
        view.setPatientNo(patientInfo.getPatientNo());
        view.setPatientName(maskPatientName(patientInfo.getPatientName()));
        if (visitRecord != null) {
            view.setVisitId(visitRecord.getId());
            view.setVisitNo(visitRecord.getVisitNo());
            view.setVisitStatus(visitRecord.getStatus());
            view.setVisitStatusText(formatVisitStatus(visitRecord.getStatus()));
        } else {
            view.setVisitStatus(patientInfo.getCurrentStatus());
            view.setVisitStatusText(formatVisitStatus(patientInfo.getCurrentStatus()));
        }

        if (ticket == null) {
            view.setHasActiveQueue(false);
            view.setQueueStatus("NONE");
            view.setQueueStatusText("暂无排队票据");
            view.setQueueMessage(buildNoTicketMessage(view.getVisitStatus()));
            fillDeptAndRoom(view,
                    visitRecord != null ? visitRecord.getCurrentDeptId() : patientInfo.getCurrentDeptId(),
                    visitRecord != null ? visitRecord.getCurrentRoomId() : patientInfo.getCurrentRoomId());
            fillAiSuggestion(view, assessment);
            view.setNextStep(buildNextStep(view));
            return view;
        }

        view.setHasActiveQueue(isActiveQueueStatus(ticket.getStatus()));
        view.setTicketNo(ticket.getTicketNo());
        view.setQueueStatus(ticket.getStatus());
        boolean waitingForConsultation = isWaitingForConsultation(ticket, roomRank);
        view.setWaitingForConsultation(waitingForConsultation);
        view.setRoomAssignmentStatus(ticket.getRoomAssignmentStatus());
        view.setQueueStatusText(formatQueueStatus(ticket.getStatus(), waitingForConsultation));
        view.setQueueMessage(formatQueueMessage(ticket.getStatus(), waitingForConsultation));
        view.setTriageLevel(ticket.getTriageLevel());
        view.setEnqueueTime(ticket.getEnqueueTime());
        view.setCallTime(ticket.getCallTime());
        view.setCompleteTime(ticket.getCompleteTime());
        view.setWaitedMinutes(ticket.getWaitedMinutes());
        view.setDeptId(ticket.getDeptId());
        view.setRoomId(ticket.getRoomId());
        view.setRank(rank != null ? rank.getRank() : ticket.getRank());
        view.setWaitingCount(rank != null ? rank.getWaitingCount() : ticket.getWaitingCount());
        view.setEstimatedWaitMinutes(rank != null ? rank.getEstimatedWaitMinutes() : ticket.getEstimatedWaitMinutes());
        view.setRoomWaitingCount(roomRank != null ? roomRank.getWaitingCount() : null);
        view.setRoomEstimatedWaitMinutes(roomRank != null ? roomRank.getEstimatedWaitMinutes() : null);
        view.setPriorityReason(ticket.getPriorityReason());
        view.setQueueStrategyMode(ticket.getQueueStrategyMode());
        view.setSurgePriorityApplied(ticket.getSurgePriorityApplied());
        view.setAgingBoostApplied(ticket.getAgingBoostApplied());
        view.setAiPriorityAdvice(ticket.getAiPriorityAdvice());
        fillDeptAndRoom(view, ticket.getDeptId(), ticket.getRoomId());
        fillAiSuggestion(view, assessment);
        view.setNextStep(buildNextStep(view));
        return view;
    }

    private TriageAssessment resolveLatestAssessment(VisitRecord visitRecord, QueueTicketVO ticket) {
        if (ticket != null && ticket.getAssessmentId() != null) {
            TriageAssessment byTicket = triageAssessmentMapper.selectById(ticket.getAssessmentId());
            if (byTicket != null) {
                return byTicket;
            }
        }
        if (visitRecord == null) {
            return null;
        }
        return triageAssessmentMapper.selectOne(new LambdaQueryWrapper<TriageAssessment>()
                .eq(TriageAssessment::getVisitId, visitRecord.getId())
                .orderByDesc(TriageAssessment::getAssessedTime, TriageAssessment::getId)
                .last("limit 1"));
    }

    private void fillAiSuggestion(PatientQueueViewVO view, TriageAssessment assessment) {
        if (assessment == null) {
            return;
        }
        view.setAiSuggestedLevel(assessment.getAiSuggestedLevel());
        view.setAiSuggestedDeptId(assessment.getAiSuggestedDeptId());
        view.setAiSuggestedDeptName(resolveDeptName(assessment.getAiSuggestedDeptId()));
        view.setAiRiskLevel(assessment.getAiRiskLevel());
        view.setAiRiskTags(splitTags(assessment.getAiRiskTags()));
        view.setAiStructuredSymptoms(splitTags(assessment.getSymptomTags()));
        view.setAiAdvice(assessment.getAiAdvice());
        view.setAiConfidence(assessment.getAiConfidence() == null ? null : assessment.getAiConfidence().doubleValue());
        view.setAiNeedManualReview(assessment.getAiNeedManualReview());
        view.setAiSource(assessment.getAiSource());
        view.setAiModelVersion(assessment.getAiModelVersion());
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        ClinicDept dept = clinicDeptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    private List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean isWaitingForConsultation(QueueTicketVO ticket, QueueRankVO roomRank) {
        if (ticket != null && ticket.getWaitingForConsultation() != null) {
            return Boolean.TRUE.equals(ticket.getWaitingForConsultation());
        }
        return ticket != null
                && ticket.getRoomId() != null
                && Objects.equals(ticket.getStatus(), QueueStatusEnum.WAITING.name())
                && roomRank != null
                && Objects.equals(roomRank.getRank(), 1L);
    }

    private void fillDeptAndRoom(PatientQueueViewVO view, Long deptId, Long roomId) {
        if (deptId != null) {
            ClinicDept dept = clinicDeptMapper.selectById(deptId);
            if (dept != null) {
                view.setDeptId(dept.getId());
                view.setDeptName(dept.getDeptName());
            }
        }
        if (roomId != null) {
            ClinicRoom room = clinicRoomMapper.selectById(roomId);
            if (room != null) {
                view.setRoomId(room.getId());
                view.setRoomName(room.getRoomName());
                view.setDoctorName(room.getDoctorName());
            }
        }
    }

    private boolean isActiveQueueStatus(String queueStatus) {
        return Objects.equals(queueStatus, QueueStatusEnum.WAITING.name())
                || Objects.equals(queueStatus, QueueStatusEnum.CALLING.name())
                || Objects.equals(queueStatus, QueueStatusEnum.MISSED.name());
    }

    private String maskPatientName(String patientName) {
        if (!StringUtils.hasText(patientName)) {
            return "-";
        }
        String normalized = patientName.trim();
        if (normalized.length() <= 1) {
            return normalized + "*";
        }
        if (normalized.length() == 2) {
            return normalized.charAt(0) + "*";
        }
        return normalized.charAt(0) + "*" + normalized.charAt(normalized.length() - 1);
    }

    private String formatVisitStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "暂无就诊记录";
        }
        return switch (status) {
            case "REGISTERED" -> "已挂号";
            case "ARRIVED" -> "已到诊";
            case "TRIAGED" -> "已分诊";
            case "QUEUING" -> "排队中";
            case "IN_TREATMENT" -> "就诊中";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String formatQueueStatus(String status, boolean waitingForConsultation) {
        if (!StringUtils.hasText(status)) {
            return "暂无排队状态";
        }
        return switch (status) {
            case "WAITING" -> waitingForConsultation ? "候诊中" : "排队中";
            case "CALLING" -> "请立即前往诊室";
            case "MISSED" -> "已过号";
            case "COMPLETED" -> "本次就诊已完成";
            case "CANCELLED" -> "排队已取消";
            default -> status;
        };
    }

    private String formatQueueMessage(String status, boolean waitingForConsultation) {
        if (!StringUtils.hasText(status)) {
            return "当前暂无排队信息";
        }
        return switch (status) {
            case "WAITING" -> waitingForConsultation
                    ? "即将轮到您，请在当前诊室门口候诊，留意现场叫号与屏幕提示"
                    : "您已完成取号并进入排队，请在候诊区耐心等待，留意现场叫号信息";
            case "CALLING" -> "请立即前往诊室报到，避免错过本次叫号";
            case "MISSED" -> "已过号，请尽快联系护士台处理";
            case "COMPLETED" -> "本次就诊流程已完成，如需复诊请咨询工作人员";
            case "CANCELLED" -> "当前排队已取消，请咨询导诊台";
            default -> "当前暂无排队信息";
        };
    }

    private String buildNoTicketMessage(String visitStatus) {
        if (!StringUtils.hasText(visitStatus)) {
            return "未查询到当前就诊记录，请先到院挂号或咨询导诊台";
        }
        return switch (visitStatus) {
            case "REGISTERED" -> "您已完成挂号，请先到院报到后再查看排队进度";
            case "ARRIVED" -> "您已到诊，请等待分诊完成后查看排队进度";
            case "TRIAGED" -> "您已完成分诊，当前暂未生成排队票据，请留意护士台通知";
            case "IN_TREATMENT" -> "当前已进入就诊中，请以现场安排为准";
            case "COMPLETED" -> "本次就诊已完成";
            case "CANCELLED" -> "当前就诊已取消，请咨询导诊台";
            default -> "当前暂无排队票据，请留意现场安排";
        };
    }

    private PatientQueueNextStepVO buildNextStep(PatientQueueViewVO view) {
        PatientQueueNextStepVO nextStep = new PatientQueueNextStepVO();
        String queueStatus = StringUtils.hasText(view.getQueueStatus()) ? view.getQueueStatus() : "NONE";
        if (Objects.equals(queueStatus, "NONE")) {
            fillNoTicketNextStep(nextStep, view);
            return nextStep;
        }

        nextStep.setStage(queueStatus);
        switch (queueStatus) {
            case "WAITING" -> fillWaitingNextStep(nextStep, view);
            case "CALLING" -> {
                nextStep.setTitle("请立刻前往诊室");
                nextStep.setAction("系统正在叫号，请立即前往对应诊室报到。");
                nextStep.setLocationHint(buildConsultationLocation(view));
                nextStep.setUrgency("IMMEDIATE");
            }
            case "MISSED" -> {
                nextStep.setTitle("请尽快联系现场工作人员");
                nextStep.setAction("您已过号，请尽快联系导诊台或诊室处理。");
                nextStep.setLocationHint(buildServiceDeskLocation(view));
                nextStep.setUrgency("HIGH");
            }
            case "COMPLETED" -> {
                nextStep.setTitle("本次接诊已完成");
                nextStep.setAction("当前无需继续候诊，如需复诊请咨询工作人员。");
                nextStep.setLocationHint(buildCompletionLocation(view));
                nextStep.setUrgency("LOW");
            }
            case "CANCELLED" -> {
                nextStep.setTitle("请咨询导诊台");
                nextStep.setAction("当前排队已取消，请联系导诊台确认后续安排。");
                nextStep.setLocationHint(buildServiceDeskLocation(view));
                nextStep.setUrgency("HIGH");
            }
            default -> {
                nextStep.setTitle("请留意现场安排");
                nextStep.setAction("系统已返回当前状态，请以现场通知为准。");
                nextStep.setLocationHint(buildGeneralLocation(view));
                nextStep.setUrgency("NORMAL");
            }
        }
        return nextStep;
    }

    private void fillNoTicketNextStep(PatientQueueNextStepVO nextStep, PatientQueueViewVO view) {
        String visitStatus = StringUtils.hasText(view.getVisitStatus()) ? view.getVisitStatus() : "";
        nextStep.setStage(StringUtils.hasText(visitStatus) ? visitStatus : "NONE");
        switch (visitStatus) {
            case "REGISTERED" -> {
                nextStep.setTitle("请先完成到院报到");
                nextStep.setAction("请前往导诊台或报到点完成报到，再查看排队进度。");
                nextStep.setLocationHint("导诊台 / 报到点");
                nextStep.setUrgency("HIGH");
            }
            case "ARRIVED" -> {
                nextStep.setTitle("请等待分诊完成");
                nextStep.setAction("您已完成到诊，请在分诊区耐心等待工作人员安排。");
                nextStep.setLocationHint(buildTriageLocation(view));
                nextStep.setUrgency("NORMAL");
            }
            case "TRIAGED" -> {
                nextStep.setTitle("等待系统入队");
                nextStep.setAction("您已完成分诊，正在等待系统入队或人工安排，请留意护士台通知。");
                nextStep.setLocationHint(buildNurseStationLocation(view));
                nextStep.setUrgency("NORMAL");
            }
            case "IN_TREATMENT" -> {
                nextStep.setTitle("请按现场接诊安排进行");
                nextStep.setAction("当前已进入就诊中，请以现场工作人员引导为准。");
                nextStep.setLocationHint(buildConsultationLocation(view));
                nextStep.setUrgency("HIGH");
            }
            case "COMPLETED" -> {
                nextStep.setTitle("本次接诊已完成");
                nextStep.setAction("当前无需继续候诊，如需复诊请咨询工作人员。");
                nextStep.setLocationHint(buildCompletionLocation(view));
                nextStep.setUrgency("LOW");
            }
            case "CANCELLED" -> {
                nextStep.setTitle("请咨询导诊台");
                nextStep.setAction("当前就诊已取消，请联系导诊台确认后续安排。");
                nextStep.setLocationHint(buildServiceDeskLocation(view));
                nextStep.setUrgency("HIGH");
            }
            default -> {
                nextStep.setTitle("请先确认当前就诊状态");
                nextStep.setAction("未查询到有效排队信息，请前往导诊台或咨询现场工作人员。");
                nextStep.setLocationHint(buildServiceDeskLocation(view));
                nextStep.setUrgency("NORMAL");
            }
        }
    }

    private void fillWaitingNextStep(PatientQueueNextStepVO nextStep, PatientQueueViewVO view) {
        boolean nearTurn = isNearTurn(view);
        if (nearTurn) {
            nextStep.setTitle("即将轮到您");
            nextStep.setAction("请立即前往诊室门口候诊，留意现场叫号与屏幕提示。");
            nextStep.setLocationHint(buildConsultationLocation(view));
            nextStep.setUrgency("HIGH");
            return;
        }
        nextStep.setTitle("请在候诊区等待");
        nextStep.setAction("您已进入排队，暂时不必靠近诊室，请留意屏幕与叫号广播。");
        nextStep.setLocationHint(buildWaitingLocation(view));
        nextStep.setUrgency("NORMAL");
    }

    private boolean isNearTurn(PatientQueueViewVO view) {
        return Boolean.TRUE.equals(view.getWaitingForConsultation())
                || (view.getRank() != null && view.getRank() <= 3)
                || (view.getEstimatedWaitMinutes() != null && view.getEstimatedWaitMinutes() <= 10);
    }

    private String buildWaitingLocation(PatientQueueViewVO view) {
        if (StringUtils.hasText(view.getDeptName())) {
            return view.getDeptName() + "候诊区";
        }
        if (StringUtils.hasText(view.getRoomName())) {
            return view.getRoomName() + "附近候诊区";
        }
        return "当前科室候诊区";
    }

    private String buildConsultationLocation(PatientQueueViewVO view) {
        if (StringUtils.hasText(view.getRoomName())) {
            return view.getRoomName() + "门口候诊区";
        }
        if (StringUtils.hasText(view.getDeptName())) {
            return view.getDeptName() + "候诊区";
        }
        return "对应诊室门口";
    }

    private String buildTriageLocation(PatientQueueViewVO view) {
        if (StringUtils.hasText(view.getDeptName())) {
            return view.getDeptName() + "分诊区";
        }
        return "分诊区";
    }

    private String buildNurseStationLocation(PatientQueueViewVO view) {
        if (StringUtils.hasText(view.getDeptName())) {
            return view.getDeptName() + "护士台";
        }
        return "护士台";
    }

    private String buildServiceDeskLocation(PatientQueueViewVO view) {
        if (StringUtils.hasText(view.getDeptName())) {
            return view.getDeptName() + "导诊台";
        }
        return "导诊台";
    }

    private String buildCompletionLocation(PatientQueueViewVO view) {
        if (StringUtils.hasText(view.getDeptName())) {
            return view.getDeptName() + "服务台";
        }
        return "现场服务台";
    }

    private String buildGeneralLocation(PatientQueueViewVO view) {
        if (StringUtils.hasText(view.getRoomName())) {
            return view.getRoomName();
        }
        if (StringUtils.hasText(view.getDeptName())) {
            return view.getDeptName();
        }
        return "现场服务台";
    }
}
