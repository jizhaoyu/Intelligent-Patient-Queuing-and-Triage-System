package com.hospital.triage.modules.triage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.modules.clinic.entity.po.ClinicDept;
import com.hospital.triage.modules.clinic.mapper.ClinicDeptMapper;
import com.hospital.triage.modules.triage.entity.po.TriageAiAudit;
import com.hospital.triage.modules.triage.entity.po.TriageRule;
import com.hospital.triage.modules.triage.mapper.TriageAiAuditMapper;
import com.hospital.triage.modules.triage.mapper.TriageRuleMapper;
import com.hospital.triage.modules.triage.service.PatientTriageAiService;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiRequest;
import com.hospital.triage.modules.triage.service.model.PatientTriageAiResult;
import com.hospital.triage.modules.triage.service.support.DeptRoutingSupport;
import com.hospital.triage.modules.triage.service.support.TriageRuleMatchSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PatientTriageAiServiceImpl implements PatientTriageAiService {
    private static final String SOURCE_RULE_FALLBACK = "RULE_FALLBACK";
    private static final String SOURCE_MOONSHOT = "MOONSHOT";

    private final PatientTriageAiProperties properties;
    private final ObjectMapper objectMapper;
    private final ClinicDeptMapper clinicDeptMapper;
    private final TriageRuleMapper triageRuleMapper;
    private final TriageAiAuditMapper triageAiAuditMapper;
    private volatile HttpClient httpClient;

    public PatientTriageAiServiceImpl(PatientTriageAiProperties properties,
                                      ObjectMapper objectMapper,
                                      ClinicDeptMapper clinicDeptMapper,
                                      TriageRuleMapper triageRuleMapper,
                                      TriageAiAuditMapper triageAiAuditMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clinicDeptMapper = clinicDeptMapper;
        this.triageRuleMapper = triageRuleMapper;
        this.triageAiAuditMapper = triageAiAuditMapper;
    }

    @Override
    public PatientTriageAiResult analyze(PatientTriageAiRequest request) {
        List<TriageRule> enabledRules = loadEnabledRules();
        List<TriageRuleMatchSupport.MatchedRule> matchedRules = filterMatchedRulesByAge(
                request,
                TriageRuleMatchSupport.matchRules(
                        enabledRules,
                        request == null ? null : request.getChiefComplaint(),
                        request == null ? null : request.getSymptomTags()));
        PatientTriageAiResult fallback = buildRuleFallback(request, matchedRules, null, null, null);
        if (!properties.isEnabled()) {
            return fallback;
        }
        if (!StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getBaseUrl())
                || !Objects.equals(properties.getProvider().toLowerCase(Locale.ROOT), "moonshot")) {
            return buildRuleFallback(request, matchedRules, "AI provider is not fully configured", null, null);
        }

        String requestPayload = null;
        String responsePayload = null;
        try {
            requestPayload = buildRequestPayload(request, matchedRules, fallback);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 1000)))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            responsePayload = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return buildRuleFallback(request,
                        matchedRules,
                        "Moonshot response status: " + response.statusCode(),
                        requestPayload,
                        responsePayload);
            }
            JsonNode root = objectMapper.readTree(responsePayload);
            String modelVersion = readAsText(root, "model");
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                return buildRuleFallback(request, matchedRules, "Moonshot message content is empty", requestPayload, responsePayload);
            }
            JsonNode aiJson = parseAiJson(content);
            if (aiJson == null) {
                return buildRuleFallback(request, matchedRules, "Moonshot response is not valid JSON", requestPayload, responsePayload);
            }
            return mergeAiResult(request, fallback, aiJson, modelVersion, requestPayload, responsePayload);
        } catch (Exception ex) {
            return buildRuleFallback(request, matchedRules, ex.getMessage(), requestPayload, responsePayload);
        }
    }

    @Override
    public Long saveAudit(Long visitId,
                          Long assessmentId,
                          PatientTriageAiRequest request,
                          PatientTriageAiResult result,
                          Integer finalTriageLevel,
                          Integer finalPriorityScore,
                          Boolean adopted) {
        if (result == null) {
            return null;
        }
        TriageAiAudit audit = new TriageAiAudit();
        audit.setVisitId(visitId);
        audit.setAssessmentId(assessmentId);
        audit.setScene(trimToNull(request == null ? null : request.getScene(), 32));
        audit.setChiefComplaint(trimToNull(request == null ? null : request.getChiefComplaint(), 255));
        audit.setSymptomTags(trimToNull(request == null ? null : request.getSymptomTags(), 255));
        audit.setAiSuggestedLevel(result.getSuggestedLevel());
        audit.setAiSuggestedDeptId(result.getSuggestedDeptId());
        audit.setAiSuggestedDeptName(trimToNull(result.getSuggestedDeptName(), 64));
        audit.setAiPriorityScore(result.getSuggestedPriorityScore());
        audit.setAiRiskLevel(trimToNull(result.getRiskLevel(), 32));
        audit.setAiRiskTags(trimToNull(joinRiskTags(result.getRiskTags()), 255));
        audit.setAiConfidence(toDecimal(result.getConfidence(), 4));
        audit.setAiNeedManualReview(Boolean.TRUE.equals(result.getNeedManualReview()));
        audit.setAiAdvice(trimToNull(result.getAdvice(), 1000));
        audit.setAiRuleDiff(trimToNull(result.getRuleDiff(), 255));
        audit.setAiSource(trimToNull(result.getSource(), 32));
        audit.setAiProvider(trimToNull(result.getProvider(), 32));
        audit.setAiModelVersion(trimToNull(result.getModelVersion(), 64));
        audit.setRequestPayload(trimToNull(result.getRawRequest(), 65535));
        audit.setResponsePayload(trimToNull(result.getRawResponse(), 65535));
        audit.setErrorMessage(trimToNull(result.getErrorMessage(), 500));
        audit.setAdopted(adopted);
        audit.setFinalTriageLevel(finalTriageLevel);
        audit.setFinalPriorityScore(finalPriorityScore);
        triageAiAuditMapper.insert(audit);
        return audit.getId();
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 1000)))
                            .build();
                }
            }
        }
        return httpClient;
    }

    private String buildRequestPayload(PatientTriageAiRequest request,
                                       List<TriageRuleMatchSupport.MatchedRule> matchedRules,
                                       PatientTriageAiResult fallback) throws Exception {
        String systemPrompt = """
                You are a medical triage assistant.
                Return strict JSON only, without markdown fences.
                Follow the matched local triage rules when they are present.
                Required keys:
                suggestedLevel (integer 1-4),
                suggestedDeptName (string),
                suggestedDeptId (integer or null),
                suggestedPriorityScore (integer),
                riskLevel (LOW/MEDIUM/HIGH/CRITICAL),
                riskTags (array of string),
                needManualReview (boolean),
                advice (string),
                confidence (number 0-1).
                """;
        String userPrompt = buildUserPrompt(request, matchedRules, fallback);
        var root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("temperature", properties.getTemperature());
        root.put("max_tokens", properties.getMaxTokens());
        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        return objectMapper.writeValueAsString(root);
    }

    private String buildUserPrompt(PatientTriageAiRequest request,
                                   List<TriageRuleMatchSupport.MatchedRule> matchedRules,
                                   PatientTriageAiResult fallback) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("scene=").append(safe(request.getScene())).append('\n');
        builder.append("visitId=").append(request.getVisitId()).append('\n');
        builder.append("chiefComplaint=").append(safe(request.getChiefComplaint())).append('\n');
        builder.append("symptomTags=").append(safe(request.getSymptomTags())).append('\n');
        builder.append("age=").append(request.getAge()).append('\n');
        builder.append("gender=").append(safe(request.getGender())).append('\n');
        builder.append("elderly=").append(Boolean.TRUE.equals(request.getElderly())).append('\n');
        builder.append("pregnant=").append(Boolean.TRUE.equals(request.getPregnant())).append('\n');
        builder.append("child=").append(Boolean.TRUE.equals(request.getChild())).append('\n');
        builder.append("disabled=").append(Boolean.TRUE.equals(request.getDisabled())).append('\n');
        builder.append("revisit=").append(Boolean.TRUE.equals(request.getRevisit())).append('\n');
        builder.append("bodyTemperature=").append(request.getBodyTemperature()).append('\n');
        builder.append("heartRate=").append(request.getHeartRate()).append('\n');
        builder.append("bloodPressure=").append(safe(request.getBloodPressure())).append('\n');
        builder.append("bloodOxygen=").append(request.getBloodOxygen()).append('\n');
        builder.append("currentTriageLevel=").append(request.getCurrentTriageLevel()).append('\n');
        builder.append("currentRecommendDeptId=").append(request.getCurrentRecommendDeptId()).append('\n');
        builder.append("currentRecommendDeptName=").append(safe(request.getCurrentRecommendDeptName())).append('\n');
        builder.append("selectedDeptId=").append(request.getSelectedDeptId()).append('\n');
        builder.append("selectedDeptName=").append(safe(request.getSelectedDeptName())).append('\n');
        builder.append("ruleFallbackLevel=").append(fallback == null ? null : fallback.getSuggestedLevel()).append('\n');
        builder.append("ruleFallbackDeptId=").append(fallback == null ? null : fallback.getSuggestedDeptId()).append('\n');
        builder.append("ruleFallbackRiskLevel=").append(safe(fallback == null ? null : fallback.getRiskLevel())).append('\n');
        builder.append("matchedRules=").append(buildMatchedRulePrompt(matchedRules)).append('\n');
        return builder.toString();
    }

    private JsonNode parseAiJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception ignored) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(content.substring(start, end + 1));
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private PatientTriageAiResult mergeAiResult(PatientTriageAiRequest request,
                                                PatientTriageAiResult fallback,
                                                JsonNode aiJson,
                                                String modelVersion,
                                                String rawRequest,
                                                String rawResponse) {
        Integer level = readAsInteger(aiJson, "suggestedLevel", "aiSuggestedLevel", "triageLevel");
        Long deptId = readAsLong(aiJson, "suggestedDeptId", "aiSuggestedDeptId");
        String deptName = readAsText(aiJson, "suggestedDeptName", "aiSuggestedDeptName");
        Integer priority = readAsInteger(aiJson, "suggestedPriorityScore", "aiPriorityScore", "priorityScore");
        String riskLevel = normalizeRiskLevel(readAsText(aiJson, "riskLevel", "aiRiskLevel"));
        List<String> riskTags = readAsStringList(aiJson, "riskTags", "aiRiskTags");
        Boolean needManualReview = readAsBoolean(aiJson, "needManualReview", "aiNeedManualReview");
        String advice = readAsText(aiJson, "advice", "aiAdvice");
        Double confidence = normalizeConfidence(readAsDouble(aiJson, "confidence", "aiConfidence"));

        if (deptId == null && StringUtils.hasText(deptName)) {
            deptId = findDeptIdByKeyword(deptName);
        }
        if (!StringUtils.hasText(deptName) && deptId != null) {
            deptName = resolveDeptName(deptId);
        }
        if (!isPediatricPatient(request) && isPediatricsDept(deptId, deptName)) {
            deptId = null;
            deptName = null;
        }

        level = level == null ? fallback.getSuggestedLevel() : Math.max(1, Math.min(level, 4));
        deptId = deptId == null ? fallback.getSuggestedDeptId() : deptId;
        deptName = StringUtils.hasText(deptName) ? deptName : fallback.getSuggestedDeptName();
        priority = priority == null ? fallback.getSuggestedPriorityScore() : priority;
        riskLevel = StringUtils.hasText(riskLevel) ? riskLevel : fallback.getRiskLevel();
        riskTags = riskTags.isEmpty() ? fallback.getRiskTags() : riskTags;
        confidence = confidence == null ? fallback.getConfidence() : confidence;
        if (needManualReview == null) {
            needManualReview = shouldManualReview(request, level, deptId, riskLevel);
        }
        advice = StringUtils.hasText(advice) ? advice : fallback.getAdvice();

        return PatientTriageAiResult.builder()
                .suggestedLevel(level)
                .suggestedDeptId(deptId)
                .suggestedDeptName(deptName)
                .suggestedPriorityScore(priority)
                .riskLevel(riskLevel)
                .riskTags(riskTags)
                .needManualReview(needManualReview)
                .advice(advice)
                .confidence(confidence)
                .ruleDiff(buildRuleDiff(request, level, deptId))
                .source(SOURCE_MOONSHOT)
                .provider("moonshot")
                .modelVersion(StringUtils.hasText(modelVersion) ? modelVersion : properties.getModel())
                .rawRequest(rawRequest)
                .rawResponse(rawResponse)
                .errorMessage(null)
                .build();
    }

    private PatientTriageAiResult buildRuleFallback(PatientTriageAiRequest request,
                                                    List<TriageRuleMatchSupport.MatchedRule> matchedRules,
                                                    String errorMessage,
                                                    String rawRequest,
                                                    String rawResponse) {
        TriageRule matchedRule = matchedRules == null || matchedRules.isEmpty() ? null : matchedRules.get(0).getRule();
        Integer level = inferRuleLevel(request, matchedRule);
        Long deptId = inferRuleDeptId(request, matchedRule);
        String deptName = resolveDeptName(deptId);
        List<String> riskTags = inferRiskTags(request, level, matchedRule);
        String riskLevel = inferRiskLevel(level, request, riskTags);
        Integer priorityScore = inferPriorityScore(level, request, riskTags, matchedRule);
        Boolean needManualReview = shouldManualReview(request, level, deptId, riskLevel);
        String advice = inferAdvice(level, deptName, riskLevel, needManualReview, matchedRule);
        return PatientTriageAiResult.builder()
                .suggestedLevel(level)
                .suggestedDeptId(deptId)
                .suggestedDeptName(deptName)
                .suggestedPriorityScore(priorityScore)
                .riskLevel(riskLevel)
                .riskTags(riskTags)
                .needManualReview(needManualReview)
                .advice(advice)
                .confidence(matchedRule == null ? 0.66D : 0.82D)
                .ruleDiff(buildRuleDiff(request, level, deptId))
                .source(SOURCE_RULE_FALLBACK)
                .provider(StringUtils.hasText(properties.getProvider()) ? properties.getProvider() : "local")
                .modelVersion(StringUtils.hasText(properties.getModel()) ? properties.getModel() : "rule-fallback-db")
                .rawRequest(rawRequest)
                .rawResponse(rawResponse)
                .errorMessage(errorMessage)
                .build();
    }

    private Integer inferRuleLevel(PatientTriageAiRequest request, TriageRule matchedRule) {
        if (request.getBloodOxygen() != null && request.getBloodOxygen() < 90) {
            return 1;
        }
        if (request.getHeartRate() != null && request.getHeartRate() > 140) {
            return 2;
        }
        if (request.getBodyTemperature() != null && request.getBodyTemperature().doubleValue() >= 39.5D) {
            return 2;
        }
        if (matchedRule != null && matchedRule.getTriageLevel() != null) {
            return Math.max(1, Math.min(matchedRule.getTriageLevel(), 4));
        }
        return inferRuleLevel(request);
    }

    private Integer inferRuleLevel(PatientTriageAiRequest request) {
        if (request.getBloodOxygen() != null && request.getBloodOxygen() < 90) {
            return 1;
        }
        if (request.getHeartRate() != null && request.getHeartRate() > 140) {
            return 2;
        }
        if (request.getBodyTemperature() != null && request.getBodyTemperature().doubleValue() >= 39.5D) {
            return 2;
        }
        String symptoms = (safe(request.getSymptomTags()) + " " + safe(request.getChiefComplaint())).toLowerCase(Locale.ROOT);
        if (symptoms.contains("chest") || symptoms.contains("胸痛")
                || symptoms.contains("呼吸") || symptoms.contains("dyspnea")
                || symptoms.contains("昏迷") || symptoms.contains("大出血")) {
            return 2;
        }
        if (request.getCurrentTriageLevel() != null) {
            return Math.max(1, Math.min(request.getCurrentTriageLevel(), 4));
        }
        return 4;
    }

    private Long inferRuleDeptId(PatientTriageAiRequest request, TriageRule matchedRule) {
        if (matchedRule != null
                && matchedRule.getRecommendDeptId() != null
                && !shouldSkipPediatricsRule(request, matchedRule)) {
            return matchedRule.getRecommendDeptId();
        }
        return inferRuleDeptId(request);
    }

    private Long inferRuleDeptId(PatientTriageAiRequest request) {
        String deptCode = DeptRoutingSupport.recommendDeptCode(
                request == null ? null : request.getAge(),
                request == null ? null : request.getChild(),
                request == null ? null : request.getPregnant(),
                request == null ? null : request.getChiefComplaint(),
                request == null ? null : request.getSymptomTags());
        Long fallbackDeptId = findDeptIdByCode(deptCode);
        if (fallbackDeptId != null && !Objects.equals(deptCode, DeptRoutingSupport.GENERAL)) {
            return fallbackDeptId;
        }
        if (request.getCurrentRecommendDeptId() != null) {
            return request.getCurrentRecommendDeptId();
        }
        if (request.getSelectedDeptId() != null) {
            return request.getSelectedDeptId();
        }
        if (fallbackDeptId != null) {
            return fallbackDeptId;
        }
        return findDeptIdByCode(DeptRoutingSupport.GENERAL);
    }

    private List<String> inferRiskTags(PatientTriageAiRequest request, Integer level, TriageRule matchedRule) {
        List<String> riskTags = new ArrayList<>(inferRiskTags(request, level));
        if (matchedRule == null) {
            return riskTags;
        }
        if (!riskTags.contains("RULE_MATCHED")) {
            riskTags.add("RULE_MATCHED");
        }
        if (Integer.valueOf(1).equals(matchedRule.getFastTrack()) && !riskTags.contains("FAST_TRACK")) {
            riskTags.add("FAST_TRACK");
        }
        return riskTags;
    }

    private List<String> inferRiskTags(PatientTriageAiRequest request, Integer level) {
        Set<String> tags = new LinkedHashSet<>();
        if (request.getBloodOxygen() != null && request.getBloodOxygen() < 90) {
            tags.add("LOW_OXYGEN");
        }
        if (request.getHeartRate() != null && request.getHeartRate() > 140) {
            tags.add("HIGH_HEART_RATE");
        }
        if (request.getBodyTemperature() != null && request.getBodyTemperature().doubleValue() >= 39.5D) {
            tags.add("HIGH_FEVER");
        }
        if (Boolean.TRUE.equals(request.getPregnant())) {
            tags.add("PREGNANT");
        }
        if (Boolean.TRUE.equals(request.getChild())) {
            tags.add("CHILD");
        }
        if (Boolean.TRUE.equals(request.getElderly())) {
            tags.add("ELDERLY");
        }
        if (level != null && level <= 2) {
            tags.add("HIGH_PRIORITY_TRIAGE_LEVEL");
        }
        String symptoms = (safe(request.getSymptomTags()) + " " + safe(request.getChiefComplaint())).toLowerCase(Locale.ROOT);
        if (symptoms.contains("胸痛") || symptoms.contains("chest")) {
            tags.add("CHEST_PAIN");
        }
        if (symptoms.contains("呼吸") || symptoms.contains("dyspnea")) {
            tags.add("RESPIRATORY_DISTRESS");
        }
        if (tags.isEmpty()) {
            tags.add("GENERAL");
        }
        return new ArrayList<>(tags);
    }

    private String inferRiskLevel(Integer level, PatientTriageAiRequest request, List<String> riskTags) {
        if (request.getBloodOxygen() != null && request.getBloodOxygen() < 90) {
            return "CRITICAL";
        }
        if (level != null && level <= 2) {
            return "HIGH";
        }
        if (riskTags.stream().anyMatch(tag -> Objects.equals(tag, "HIGH_FEVER") || Objects.equals(tag, "HIGH_HEART_RATE"))) {
            return "HIGH";
        }
        if (level != null && level == 3) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Integer inferPriorityScore(Integer level,
                                       PatientTriageAiRequest request,
                                       List<String> riskTags,
                                       TriageRule matchedRule) {
        int score = inferPriorityScore(level, request, riskTags);
        if (matchedRule == null || matchedRule.getSpecialWeight() == null) {
            return score;
        }
        return score + Math.max(matchedRule.getSpecialWeight(), 0);
    }

    private Integer inferPriorityScore(Integer level, PatientTriageAiRequest request, List<String> riskTags) {
        int base = switch (level == null ? 4 : level) {
            case 1 -> 1000;
            case 2 -> 700;
            case 3 -> 400;
            default -> 200;
        };
        int bonus = 0;
        if (Boolean.TRUE.equals(request.getPregnant())) {
            bonus += 80;
        }
        if (Boolean.TRUE.equals(request.getChild())) {
            bonus += 60;
        }
        if (Boolean.TRUE.equals(request.getElderly())) {
            bonus += 40;
        }
        bonus += Math.max(riskTags.size() - 1, 0) * 15;
        return base + bonus;
    }

    private List<TriageRuleMatchSupport.MatchedRule> filterMatchedRulesByAge(PatientTriageAiRequest request,
                                                                             List<TriageRuleMatchSupport.MatchedRule> matchedRules) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return List.of();
        }
        if (isPediatricPatient(request)) {
            return matchedRules;
        }
        return matchedRules.stream()
                .filter(matchedRule -> !isPediatricsRule(matchedRule == null ? null : matchedRule.getRule()))
                .toList();
    }

    private boolean shouldSkipPediatricsRule(PatientTriageAiRequest request, TriageRule matchedRule) {
        return !isPediatricPatient(request) && isPediatricsRule(matchedRule);
    }

    private boolean isPediatricPatient(PatientTriageAiRequest request) {
        if (request == null) {
            return false;
        }
        return DeptRoutingSupport.isPediatricPatient(request.getAge(), request.getChild());
    }

    private boolean isPediatricsRule(TriageRule rule) {
        if (rule == null) {
            return false;
        }
        if (StringUtils.hasText(rule.getRuleCode())
                && rule.getRuleCode().trim().toUpperCase(Locale.ROOT).startsWith("RULE_PED")) {
            return true;
        }
        return isPediatricsDept(rule.getRecommendDeptId(), null);
    }

    private boolean isPediatricsDept(Long deptId, String deptName) {
        if (isPediatricsDeptName(deptName)) {
            return true;
        }
        if (deptId == null) {
            return false;
        }
        return isPediatricsDeptName(resolveDeptName(deptId));
    }

    private boolean isPediatricsDeptName(String deptName) {
        return StringUtils.hasText(deptName) && deptName.contains("儿科");
    }

    private boolean shouldManualReview(PatientTriageAiRequest request, Integer suggestedLevel, Long suggestedDeptId, String riskLevel) {
        if (suggestedLevel != null && suggestedLevel <= 2) {
            return true;
        }
        if (Objects.equals(riskLevel, "HIGH") || Objects.equals(riskLevel, "CRITICAL")) {
            return true;
        }
        if (request.getBloodOxygen() != null && request.getBloodOxygen() < 92) {
            return true;
        }
        if (request.getCurrentTriageLevel() != null && !Objects.equals(request.getCurrentTriageLevel(), suggestedLevel)) {
            return true;
        }
        if (request.getCurrentRecommendDeptId() != null && !Objects.equals(request.getCurrentRecommendDeptId(), suggestedDeptId)) {
            return true;
        }
        return false;
    }

    private String inferAdvice(Integer level,
                               String deptName,
                               String riskLevel,
                               Boolean needManualReview,
                               TriageRule matchedRule) {
        StringBuilder builder = new StringBuilder();
        if (matchedRule != null && StringUtils.hasText(matchedRule.getRuleName())) {
            builder.append("已命中规则“").append(matchedRule.getRuleName()).append("”。");
        }
        builder.append(inferAdvice(level, deptName, riskLevel, needManualReview));
        return builder.toString();
    }

    private String inferAdvice(Integer level, String deptName, String riskLevel, Boolean needManualReview) {
        StringBuilder builder = new StringBuilder();
        builder.append("建议分诊等级：")
                .append(level == null ? "待评估" : level + "级")
                .append("。");
        builder.append("建议就诊科室：")
                .append(StringUtils.hasText(deptName) ? deptName : "待进一步判断")
                .append("。");
        builder.append("风险等级：")
                .append(formatRiskLevelText(riskLevel))
                .append("。");
        if (Boolean.TRUE.equals(needManualReview)) {
            builder.append("建议护士人工复核。");
        }
        return builder.toString();
    }

    private String formatRiskLevelText(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return "待评估";
        }
        return switch (riskLevel) {
            case "CRITICAL" -> "危急";
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            default -> riskLevel;
        };
    }

    private String buildRuleDiff(PatientTriageAiRequest request, Integer suggestedLevel, Long suggestedDeptId) {
        List<String> diffs = new ArrayList<>();
        if (request.getCurrentTriageLevel() != null && !Objects.equals(request.getCurrentTriageLevel(), suggestedLevel)) {
            diffs.add("LEVEL_DIFF");
        }
        if (request.getCurrentRecommendDeptId() != null && !Objects.equals(request.getCurrentRecommendDeptId(), suggestedDeptId)) {
            diffs.add("DEPT_DIFF");
        }
        return diffs.isEmpty() ? "NONE" : String.join(",", diffs);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String joinRiskTags(List<String> riskTags) {
        if (riskTags == null || riskTags.isEmpty()) {
            return null;
        }
        return riskTags.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(","));
    }

    private List<TriageRule> loadEnabledRules() {
        List<TriageRule> rules = triageRuleMapper.selectList(new LambdaQueryWrapper<TriageRule>()
                .eq(TriageRule::getEnabled, 1));
        return rules == null ? List.of() : rules;
    }

    private String buildMatchedRulePrompt(List<TriageRuleMatchSupport.MatchedRule> matchedRules) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return "NONE";
        }
        return matchedRules.stream()
                .limit(5)
                .map(matchedRule -> {
                    TriageRule rule = matchedRule.getRule();
                    return String.format(Locale.ROOT,
                            "%s(keyword=%s, level=%s, deptId=%s, weight=%s, fastTrack=%s)",
                            StringUtils.hasText(rule.getRuleCode()) ? rule.getRuleCode() : "RULE",
                            matchedRule.getMatchedKeyword(),
                            rule.getTriageLevel(),
                            rule.getRecommendDeptId(),
                            rule.getSpecialWeight(),
                            rule.getFastTrack());
                })
                .collect(Collectors.joining(" | "));
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        ClinicDept dept = clinicDeptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    private Long findDeptIdByCode(String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return null;
        }
        return clinicDeptMapper.selectList(new LambdaQueryWrapper<ClinicDept>()
                        .eq(ClinicDept::getDeptCode, deptCode)
                        .eq(ClinicDept::getEnabled, 1)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .map(ClinicDept::getId)
                .orElse(null);
    }

    private Long findDeptIdByKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return clinicDeptMapper.selectList(new LambdaQueryWrapper<ClinicDept>()
                        .like(ClinicDept::getDeptName, keyword)
                        .eq(ClinicDept::getEnabled, 1)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .map(ClinicDept::getId)
                .orElse(null);
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return null;
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH", "CRITICAL" -> normalized;
            default -> null;
        };
    }

    private Double normalizeConfidence(Double confidence) {
        if (confidence == null) {
            return null;
        }
        return Math.max(0D, Math.min(1D, confidence));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private BigDecimal toDecimal(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private String readAsText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private Integer readAsInteger(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isInt() || value.isLong()) {
                    return value.intValue();
                }
                if (value.isTextual()) {
                    try {
                        return Integer.parseInt(value.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // ignore parse failure and continue
                    }
                }
            }
        }
        return null;
    }

    private Long readAsLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isLong() || value.isInt()) {
                    return value.longValue();
                }
                if (value.isTextual()) {
                    try {
                        return Long.parseLong(value.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // ignore parse failure and continue
                    }
                }
            }
        }
        return null;
    }

    private Double readAsDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isDouble() || value.isFloat() || value.isLong() || value.isInt()) {
                    return value.asDouble();
                }
                if (value.isTextual()) {
                    try {
                        return Double.parseDouble(value.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // ignore parse failure and continue
                    }
                }
            }
        }
        return null;
    }

    private Boolean readAsBoolean(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isBoolean()) {
                    return value.asBoolean();
                }
                if (value.isTextual()) {
                    String text = value.asText().trim().toLowerCase(Locale.ROOT);
                    if (Objects.equals(text, "true")) {
                        return true;
                    }
                    if (Objects.equals(text, "false")) {
                        return false;
                    }
                }
            }
        }
        return null;
    }

    private List<String> readAsStringList(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                List<String> result = new ArrayList<>();
                value.forEach(item -> {
                    if (item != null && !item.isNull() && StringUtils.hasText(item.asText())) {
                        result.add(item.asText().trim());
                    }
                });
                if (!result.isEmpty()) {
                    return result;
                }
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                String[] parts = value.asText().split("[,，]");
                List<String> result = new ArrayList<>();
                for (String part : parts) {
                    if (StringUtils.hasText(part)) {
                        result.add(part.trim());
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return List.of();
    }
}
