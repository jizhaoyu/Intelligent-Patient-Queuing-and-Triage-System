package com.hospital.triage.modules.patient.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.common.enums.QueueStatusEnum;
import com.hospital.triage.common.enums.VisitStatusEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.patient.entity.dto.PatientQueueQueryDTO;
import com.hospital.triage.modules.patient.entity.dto.PatientSelfQueueEnrollDTO;
import com.hospital.triage.modules.patient.entity.po.PatientInfo;
import com.hospital.triage.modules.patient.entity.vo.PatientQueueViewVO;
import com.hospital.triage.modules.patient.mapper.PatientInfoMapper;
import com.hospital.triage.modules.patient.service.PatientQueueQueryService;
import com.hospital.triage.modules.patient.service.PatientSelfQueueService;
import com.hospital.triage.modules.queue.service.QueueDispatchService;
import com.hospital.triage.modules.triage.entity.po.TriageAssessment;
import com.hospital.triage.modules.triage.mapper.TriageAssessmentMapper;
import com.hospital.triage.modules.triage.service.PatientTriageAiService;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiRequest;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiResult;
import com.hospital.triage.modules.visit.entity.dto.VisitCreateDTO;
import com.hospital.triage.modules.visit.entity.po.VisitRecord;
import com.hospital.triage.modules.visit.entity.vo.VisitVO;
import com.hospital.triage.modules.visit.mapper.VisitRecordMapper;
import com.hospital.triage.modules.visit.service.VisitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PatientSelfQueueServiceImpl implements PatientSelfQueueService {

    private static final String ENROLL_FAILED_MESSAGE = "未识别到有效患者信息，请前往导诊台处理";
    private static final String INVALID_DEPT_MESSAGE = "所选科室不可用，请联系导诊台";
    private static final String INVALID_MODE_MESSAGE = "患者办理方式不正确，请重新选择";
    private static final String INVALID_PHONE_MESSAGE = "手机号格式不正确，请输入11位手机号";
    private static final String NEW_PATIENT_REQUIRED_MESSAGE = "新患者请补全姓名、手机号、性别与出生日期";
    private static final String NEW_PATIENT_DUPLICATED_MESSAGE = "检测到同名同手机号的多条档案，请前往导诊台核验";
    private static final String EXISTING_MODE = "EXISTING";
    private static final String NEW_MODE = "NEW";

    private final PatientInfoMapper patientInfoMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final ClinicDeptMapper clinicDeptMapper;
    private final VisitService visitService;
    private final TriageAssessmentMapper triageAssessmentMapper;
    private final QueueDispatchService queueDispatchService;
    private final PatientQueueQueryService patientQueueQueryService;
    private final PatientTriageAiService patientTriageAiService;

    public PatientSelfQueueServiceImpl(PatientInfoMapper patientInfoMapper,
                                       VisitRecordMapper visitRecordMapper,
                                       ClinicDeptMapper clinicDeptMapper,
                                       VisitService visitService,
                                       TriageAssessmentMapper triageAssessmentMapper,
                                       QueueDispatchService queueDispatchService,
                                       PatientQueueQueryService patientQueueQueryService,
                                       PatientTriageAiService patientTriageAiService) {
        this.patientInfoMapper = patientInfoMapper;
        this.visitRecordMapper = visitRecordMapper;
        this.clinicDeptMapper = clinicDeptMapper;
        this.visitService = visitService;
        this.triageAssessmentMapper = triageAssessmentMapper;
        this.queueDispatchService = queueDispatchService;
        this.patientQueueQueryService = patientQueueQueryService;
        this.patientTriageAiService = patientTriageAiService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientQueueViewVO enroll(PatientSelfQueueEnrollDTO dto) {
        String patientMode = normalizePatientMode(dto.getPatientMode());

        ClinicDept dept = clinicDeptMapper.selectById(dto.getDeptId());
        if (dept == null || !Objects.equals(dept.getEnabled(), 1)) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), INVALID_DEPT_MESSAGE);
        }

        PatientInfo patientInfo;
        PatientQueueQueryDTO queryDTO;
        if (NEW_MODE.equals(patientMode)) {
            patientInfo = resolveOrCreateNewPatient(dto);
            queryDTO = buildPatientNoQuery(patientInfo);
        } else {
            String phoneSuffix = normalizePhoneSuffix(dto.getPhoneSuffix());
            String patientNo = normalizeIdentifier(dto.getPatientNo());
            String patientName = normalizeIdentifier(dto.getPatientName());
            patientInfo = resolveExistingPatient(patientNo, patientName, phoneSuffix);
            queryDTO = new PatientQueueQueryDTO();
            queryDTO.setPatientName(patientName);
            queryDTO.setPatientNo(patientNo);
            queryDTO.setPhoneSuffix(phoneSuffix);
        }

        PatientQueueViewVO currentView = patientQueueQueryService.query(queryDTO);
        if (currentView.isHasActiveQueue()
                && isActiveQueueStatus(currentView.getQueueStatus())) {
            return currentView;
        }

        VisitRecord visitRecord = resolveOrCreateVisit(patientInfo, dto, currentView);
        PatientTriageAiRequest aiRequest = buildSelfQueueAiRequest(patientInfo, visitRecord, dto, dept);
        PatientTriageAiResult aiResult = patientTriageAiService.analyze(aiRequest);
        TriageAssessment assessment = buildKioskAssessment(patientInfo, visitRecord, dto, dept, aiResult);
        triageAssessmentMapper.insert(assessment);
        Long aiAuditId = patientTriageAiService.saveAudit(visitRecord.getId(), assessment.getId(),
                aiRequest, aiResult, assessment.getTriageLevel(), assessment.getPriorityScore(), isAiAdopted(assessment, aiResult));
        if (aiAuditId != null) {
            assessment.setAiAuditId(aiAuditId);
            triageAssessmentMapper.updateById(assessment);
        }

        queueDispatchService.enqueueFromKiosk(visitRecord.getId(), assessment.getId());

        return patientQueueQueryService.query(queryDTO);
    }

    private boolean isActiveQueueStatus(String queueStatus) {
        return Objects.equals(queueStatus, QueueStatusEnum.WAITING.name())
                || Objects.equals(queueStatus, QueueStatusEnum.CALLING.name())
                || Objects.equals(queueStatus, QueueStatusEnum.MISSED.name());
    }

    private String normalizePatientMode(String patientMode) {
        String normalized = normalizeIdentifier(patientMode);
        if (!StringUtils.hasText(normalized)) {
            return EXISTING_MODE;
        }
        String upperCaseMode = normalized.toUpperCase(Locale.ROOT);
        if (!Objects.equals(upperCaseMode, EXISTING_MODE) && !Objects.equals(upperCaseMode, NEW_MODE)) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), INVALID_MODE_MESSAGE);
        }
        return upperCaseMode;
    }

    private PatientQueueQueryDTO buildPatientNoQuery(PatientInfo patientInfo) {
        PatientQueueQueryDTO queryDTO = new PatientQueueQueryDTO();
        queryDTO.setPatientNo(patientInfo.getPatientNo());
        queryDTO.setPhoneSuffix(extractPhoneSuffix(patientInfo.getPhone()));
        return queryDTO;
    }

    private PatientInfo resolveOrCreateNewPatient(PatientSelfQueueEnrollDTO dto) {
        String patientName = normalizeIdentifier(dto.getPatientName());
        String phone = normalizePhone(dto.getPhone());
        String gender = normalizeGender(dto.getGender());
        LocalDate birthDate = dto.getBirthDate();
        if (!StringUtils.hasText(patientName) || !StringUtils.hasText(gender) || birthDate == null) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), NEW_PATIENT_REQUIRED_MESSAGE);
        }

        List<PatientInfo> exactMatches = patientInfoMapper.selectList(new LambdaQueryWrapper<PatientInfo>()
                .eq(PatientInfo::getPatientName, patientName)
                .eq(PatientInfo::getPhone, phone)
                .orderByDesc(PatientInfo::getId));
        if (exactMatches.size() > 1) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), NEW_PATIENT_DUPLICATED_MESSAGE);
        }
        if (!exactMatches.isEmpty()) {
            PatientInfo existing = exactMatches.get(0);
            syncOptionalPatientProfile(existing, dto);
            return existing;
        }

        PatientInfo patientInfo = new PatientInfo();
        patientInfo.setPatientNo("P" + RandomUtil.randomNumbers(10));
        patientInfo.setPatientName(patientName);
        patientInfo.setPhone(phone);
        patientInfo.setGender(gender);
        patientInfo.setBirthDate(birthDate);
        patientInfo.setIdCard(normalizeIdentifier(dto.getIdCard()));
        patientInfo.setAllergyHistory(normalizeOptionalText(dto.getAllergyHistory()));
        patientInfo.setSpecialTags(normalizeOptionalText(dto.getSpecialTags()));
        patientInfoMapper.insert(patientInfo);
        return patientInfo;
    }

    private PatientInfo resolveExistingPatient(String patientNo, String patientName, String phoneSuffix) {
        boolean hasPatientNo = StringUtils.hasText(patientNo);
        boolean hasPatientName = StringUtils.hasText(patientName);
        if (hasPatientNo == hasPatientName) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), ENROLL_FAILED_MESSAGE);
        }
        if (hasPatientNo) {
            PatientInfo patientInfo = patientInfoMapper.selectOne(new LambdaQueryWrapper<PatientInfo>()
                    .eq(PatientInfo::getPatientNo, patientNo)
                    .last("limit 1"));
            validatePatientCredential(patientInfo, phoneSuffix);
            return patientInfo;
        }
        List<PatientInfo> candidates = patientInfoMapper.selectList(new LambdaQueryWrapper<PatientInfo>()
                .eq(PatientInfo::getPatientName, patientName));
        List<PatientInfo> matched = candidates.stream()
                .filter(candidate -> hasMatchingPhoneSuffix(candidate, phoneSuffix))
                .toList();
        if (matched.size() != 1) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), ENROLL_FAILED_MESSAGE);
        }
        return matched.get(0);
    }

    private void validatePatientCredential(PatientInfo patientInfo, String phoneSuffix) {
        if (patientInfo == null || !hasMatchingPhoneSuffix(patientInfo, phoneSuffix)) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), ENROLL_FAILED_MESSAGE);
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

    private String normalizePhoneSuffix(String phoneSuffix) {
        String normalized = normalizeIdentifier(phoneSuffix);
        if (!StringUtils.hasText(normalized) || !normalized.matches("\\d{4}")) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), ENROLL_FAILED_MESSAGE);
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        String normalized = normalizeIdentifier(phone);
        if (!StringUtils.hasText(normalized) || !normalized.matches("1\\d{10}")) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), INVALID_PHONE_MESSAGE);
        }
        return normalized;
    }

    private String normalizeGender(String gender) {
        String normalized = normalizeIdentifier(gender);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String upperCaseGender = normalized.toUpperCase(Locale.ROOT);
        if (!Objects.equals(upperCaseGender, "MALE") && !Objects.equals(upperCaseGender, "FEMALE")) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), NEW_PATIENT_REQUIRED_MESSAGE);
        }
        return upperCaseGender;
    }

    private String extractPhoneSuffix(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 4) {
            throw new ServiceException(ErrorCodeEnum.BAD_REQUEST.getCode(), INVALID_PHONE_MESSAGE);
        }
        return phone.substring(phone.length() - 4);
    }

    private String normalizeIdentifier(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeOptionalText(String value) {
        String normalized = normalizeIdentifier(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private void syncOptionalPatientProfile(PatientInfo patientInfo, PatientSelfQueueEnrollDTO dto) {
        String allergyHistory = normalizeOptionalText(dto.getAllergyHistory());
        String specialTags = normalizeOptionalText(dto.getSpecialTags());
        boolean changed = false;

        if (StringUtils.hasText(allergyHistory) && !Objects.equals(allergyHistory, patientInfo.getAllergyHistory())) {
            patientInfo.setAllergyHistory(allergyHistory);
            changed = true;
        }
        if (StringUtils.hasText(specialTags) && !Objects.equals(specialTags, patientInfo.getSpecialTags())) {
            patientInfo.setSpecialTags(specialTags);
            changed = true;
        }

        if (changed) {
            patientInfoMapper.updateById(patientInfo);
        }
    }

    private VisitRecord resolveOrCreateVisit(PatientInfo patientInfo,
                                             PatientSelfQueueEnrollDTO dto,
                                             PatientQueueViewVO currentView) {
        VisitRecord visitRecord = null;
        if (currentView != null && currentView.getVisitId() != null) {
            VisitRecord currentVisit = visitRecordMapper.selectById(currentView.getVisitId());
            if (currentVisit != null
                    && Objects.equals(currentVisit.getPatientId(), patientInfo.getId())
                    && !Objects.equals(currentVisit.getStatus(), VisitStatusEnum.COMPLETED.name())
                    && !Objects.equals(currentVisit.getStatus(), VisitStatusEnum.CANCELLED.name())) {
                visitRecord = currentVisit;
            }
        }

        if (visitRecord == null) {
            VisitCreateDTO createDTO = new VisitCreateDTO();
            createDTO.setPatientId(patientInfo.getId());
            String chiefComplaint = StringUtils.hasText(dto.getChiefComplaint())
                    ? dto.getChiefComplaint().trim()
                    : "自助机取号";
            createDTO.setChiefComplaint(chiefComplaint);
            VisitVO visitVO = visitService.create(createDTO);
            visitRecord = visitRecordMapper.selectById(visitVO.getId());
            VisitVO arrived = visitService.arrive(visitRecord.getId());
            visitRecord = visitRecordMapper.selectById(arrived.getId());
        }

        if (!Objects.equals(visitRecord.getStatus(), VisitStatusEnum.ARRIVED.name())
                && !Objects.equals(visitRecord.getStatus(), VisitStatusEnum.TRIAGED.name())
                && !Objects.equals(visitRecord.getStatus(), VisitStatusEnum.QUEUING.name())
                && !Objects.equals(visitRecord.getStatus(), VisitStatusEnum.IN_TREATMENT.name())) {
            VisitVO arrived = visitService.arrive(visitRecord.getId());
            visitRecord = visitRecordMapper.selectById(arrived.getId());
        }

        if (!Objects.equals(visitRecord.getCurrentDeptId(), dto.getDeptId())) {
            visitRecord.setCurrentDeptId(dto.getDeptId());
            visitRecordMapper.updateById(visitRecord);
        }

        return visitRecord;
    }

    private TriageAssessment buildKioskAssessment(PatientInfo patientInfo,
                                                  VisitRecord visitRecord,
                                                  PatientSelfQueueEnrollDTO dto,
                                                  ClinicDept dept,
                                                  PatientTriageAiResult aiResult) {
        TriageAssessment assessment = new TriageAssessment();
        assessment.setVisitId(visitRecord.getId());
        String symptom = StringUtils.hasText(dto.getChiefComplaint())
                ? dto.getChiefComplaint().trim()
                : "自助机未填写主诉";
        assessment.setSymptomTags(symptom);

        if (patientInfo.getBirthDate() != null) {
            LocalDate birth = patientInfo.getBirthDate();
            int age = Math.max(0, Period.between(birth, LocalDate.now()).getYears());
            assessment.setAge(age);
            assessment.setChild(age < 14);
            assessment.setElderly(age >= 65);
        }
        assessment.setGender(patientInfo.getGender());
        assessment.setDisabled(containsAny(dto.getSpecialTags(), "轮椅", "残障", "行动不便"));
        assessment.setPregnant(containsAny(dto.getSpecialTags(), "孕", "孕妇"));
        assessment.setRevisit(containsAny(dto.getSpecialTags(), "复诊", "复查"));
        assessment.setTriageLevel(resolveAiLevel(aiResult));
        assessment.setRecommendDeptId(resolveAiDeptId(aiResult, dept.getId()));
        assessment.setPriorityScore(resolveAiPriorityScore(aiResult));
        assessment.setFastTrack(resolveAiFastTrack(aiResult));
        assessment.setManualAdjustScore(0);
        if (aiResult != null) {
            assessment.setAiSuggestedLevel(aiResult.getSuggestedLevel());
            assessment.setAiSuggestedDeptId(aiResult.getSuggestedDeptId());
            assessment.setAiPriorityScore(aiResult.getSuggestedPriorityScore());
            assessment.setAiRiskLevel(aiResult.getRiskLevel());
            assessment.setAiRiskTags(joinRiskTags(aiResult.getRiskTags()));
            assessment.setAiConfidence(toConfidence(aiResult.getConfidence()));
            assessment.setAiAdvice(aiResult.getAdvice());
            assessment.setAiNeedManualReview(Boolean.TRUE.equals(aiResult.getNeedManualReview()));
            assessment.setAiRuleDiff(aiResult.getRuleDiff());
            assessment.setAiModelVersion(aiResult.getModelVersion());
            assessment.setAiSource(aiResult.getSource());
        }
        assessment.setAssessor("kiosk");
        assessment.setAssessedTime(LocalDateTime.now());
        return assessment;
    }

    private PatientTriageAiRequest buildSelfQueueAiRequest(PatientInfo patientInfo,
                                                           VisitRecord visitRecord,
                                                           PatientSelfQueueEnrollDTO dto,
                                                           ClinicDept dept) {
        Integer age = null;
        if (patientInfo.getBirthDate() != null) {
            age = Math.max(0, Period.between(patientInfo.getBirthDate(), LocalDate.now()).getYears());
        }
        String symptom = StringUtils.hasText(dto.getChiefComplaint()) ? dto.getChiefComplaint().trim() : null;
        return PatientTriageAiRequest.builder()
                .scene("SELF_QUEUE")
                .visitId(visitRecord.getId())
                .chiefComplaint(symptom)
                .symptomTags(symptom)
                .age(age)
                .gender(patientInfo.getGender())
                .elderly(age != null && age >= 65)
                .pregnant(containsAny(dto.getSpecialTags(), "孕", "孕妇"))
                .child(age != null && age < 14)
                .disabled(containsAny(dto.getSpecialTags(), "轮椅", "残障", "行动不便"))
                .revisit(containsAny(dto.getSpecialTags(), "复诊", "复查"))
                .selectedDeptId(dto.getDeptId())
                .selectedDeptName(dept.getDeptName())
                .currentTriageLevel(4)
                .currentRecommendDeptId(dept.getId())
                .currentRecommendDeptName(dept.getDeptName())
                .build();
    }

    private boolean isAiAdopted(TriageAssessment assessment, PatientTriageAiResult aiResult) {
        return aiResult != null
                && Objects.equals(assessment.getTriageLevel(), aiResult.getSuggestedLevel())
                && Objects.equals(assessment.getRecommendDeptId(), aiResult.getSuggestedDeptId())
                && Objects.equals(assessment.getPriorityScore(), aiResult.getSuggestedPriorityScore());
    }

    private Integer resolveAiLevel(PatientTriageAiResult aiResult) {
        Integer level = aiResult == null ? null : aiResult.getSuggestedLevel();
        return level == null ? 4 : Math.max(1, Math.min(level, 4));
    }

    private Long resolveAiDeptId(PatientTriageAiResult aiResult, Long fallbackDeptId) {
        return aiResult != null && aiResult.getSuggestedDeptId() != null ? aiResult.getSuggestedDeptId() : fallbackDeptId;
    }

    private Integer resolveAiPriorityScore(PatientTriageAiResult aiResult) {
        Integer score = aiResult == null ? null : aiResult.getSuggestedPriorityScore();
        return score == null ? 0 : Math.max(score, 0);
    }

    private Integer resolveAiFastTrack(PatientTriageAiResult aiResult) {
        return aiResult != null && aiResult.getSuggestedLevel() != null && aiResult.getSuggestedLevel() <= 2 ? 1 : 0;
    }

    private boolean containsAny(String source, String... keywords) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        String normalized = source.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String joinRiskTags(List<String> riskTags) {
        if (riskTags == null || riskTags.isEmpty()) {
            return null;
        }
        String result = riskTags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(","));
        return StringUtils.hasText(result) ? result : null;
    }

    private BigDecimal toConfidence(Double confidence) {
        if (confidence == null) {
            return null;
        }
        return BigDecimal.valueOf(Math.max(0D, Math.min(1D, confidence)));
    }
}
