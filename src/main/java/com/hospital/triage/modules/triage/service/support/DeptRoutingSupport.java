package com.hospital.triage.modules.triage.service.support;

import org.springframework.util.StringUtils;

import java.util.List;

public final class DeptRoutingSupport {

    public static final String EMERGENCY = "EMERGENCY";
    public static final String PEDIATRICS = "PEDIATRICS";
    public static final String OBSTETRICS = "OBSTETRICS";
    public static final String GENERAL = "GENERAL";
    public static final String CARDIOLOGY = "CARDIOLOGY";
    public static final String RESPIRATORY = "RESPIRATORY";
    public static final String NEUROLOGY = "NEUROLOGY";
    public static final String ORTHOPEDICS = "ORTHOPEDICS";

    private static final int PEDIATRIC_MAX_AGE = 14;

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "胸痛", "压榨性胸痛", "急性气促", "呼吸急促", "呼吸困难", "呼吸窘迫", "喘不上气",
            "意识不清", "昏迷", "晕厥", "休克", "大出血", "呕血", "咯血", "便血", "黑便",
            "剧烈腹痛", "爆炸样头痛", "偏瘫", "口角歪斜", "一侧无力", "抽搐",
            "外伤", "骨折", "刀伤", "刺伤", "烧伤", "烫伤", "脱位",
            "中毒", "药物中毒", "农药中毒", "过敏性休克", "喉头水肿",
            "chest pain", "dyspnea", "syncope", "unconscious", "stroke", "trauma", "fracture", "poisoning");

    private static final List<String> OBSTETRICS_KEYWORDS = List.of(
            "孕", "见红", "破水", "宫缩", "胎动减少", "阴道出血", "先兆流产");

    private static final List<String> CARDIOLOGY_KEYWORDS = List.of(
            "活动后胸闷", "劳累后胸闷", "间断心悸", "偶发心慌", "心前区不适", "胸口隐痛",
            "血压波动", "反复血压升高", "心悸", "心慌", "胸闷",
            "palpitation", "precordial", "blood pressure");

    private static final List<String> RESPIRATORY_KEYWORDS = List.of(
            "持续咳嗽", "反复咳嗽", "咳嗽咳痰", "黄痰咳嗽", "夜间咳嗽", "晨起咳痰",
            "过敏性咳嗽", "咽痒干咳", "咳嗽", "咳痰", "干咳",
            "cough", "sputum");

    private static final List<String> NEUROLOGY_KEYWORDS = List.of(
            "反复头痛", "慢性头痛", "偏头痛", "眩晕耳鸣", "头晕耳鸣",
            "手脚发麻", "肢体麻木", "静止性震颤", "手抖震颤", "头痛", "头晕", "眩晕", "耳鸣",
            "headache", "dizziness", "vertigo", "numbness", "tremor");

    private static final List<String> ORTHOPEDICS_KEYWORDS = List.of(
            "腰肌劳损", "腰背酸痛", "脚踝扭伤", "踝关节扭伤", "膝关节痛", "膝盖酸痛",
            "颈肩疼痛", "肩颈酸痛", "扭伤", "关节痛", "肩痛", "颈痛", "腰痛",
            "sprain", "joint pain", "back pain", "neck pain", "shoulder pain");

    private DeptRoutingSupport() {
    }

    public static String recommendDeptCode(Integer age,
                                           Boolean child,
                                           Boolean pregnant,
                                           String chiefComplaint,
                                           String symptomTags) {
        String normalizedInput = TriageRuleMatchSupport.normalizeInput(chiefComplaint, symptomTags);
        if (containsAny(normalizedInput, EMERGENCY_KEYWORDS)) {
            return EMERGENCY;
        }
        if (isPediatricPatient(age, child)) {
            return PEDIATRICS;
        }
        if (Boolean.TRUE.equals(pregnant) || containsAny(normalizedInput, OBSTETRICS_KEYWORDS)) {
            return OBSTETRICS;
        }
        if (containsAny(normalizedInput, CARDIOLOGY_KEYWORDS)) {
            return CARDIOLOGY;
        }
        if (containsAny(normalizedInput, RESPIRATORY_KEYWORDS)) {
            return RESPIRATORY;
        }
        if (containsAny(normalizedInput, NEUROLOGY_KEYWORDS)) {
            return NEUROLOGY;
        }
        if (containsAny(normalizedInput, ORTHOPEDICS_KEYWORDS)) {
            return ORTHOPEDICS;
        }
        return GENERAL;
    }

    public static boolean isPediatricPatient(Integer age, Boolean child) {
        if (age != null) {
            return age < PEDIATRIC_MAX_AGE;
        }
        return Boolean.TRUE.equals(child);
    }

    private static boolean containsAny(String normalizedInput, List<String> keywords) {
        if (!StringUtils.hasText(normalizedInput)) {
            return false;
        }
        return keywords.stream().anyMatch(normalizedInput::contains);
    }
}
