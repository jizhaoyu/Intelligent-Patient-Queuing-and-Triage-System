-- 分诊规则补充初始化脚本
-- 适用场景：根据当前项目的简化分诊规则模型，补充一批更贴近医院门急诊真实场景的规则数据
-- 执行前提：clinic_dept 已存在以下科室编码：EMERGENCY / PEDIATRICS / OBSTETRICS / GENERAL
-- 注意：当前系统规则命中方式为“symptom_tags 包含 symptom_keyword”，因此此处关键词尽量使用短中文关键词

START TRANSACTION;

SET @seed_now := NOW();
SET @dept_emergency := (SELECT id FROM clinic_dept WHERE dept_code = 'EMERGENCY' LIMIT 1);
SET @dept_pediatrics := (SELECT id FROM clinic_dept WHERE dept_code = 'PEDIATRICS' LIMIT 1);
SET @dept_obstetrics := (SELECT id FROM clinic_dept WHERE dept_code = 'OBSTETRICS' LIMIT 1);
SET @dept_general := (SELECT id FROM clinic_dept WHERE dept_code = 'GENERAL' LIMIT 1);

INSERT INTO triage_rule (
    rule_code,
    rule_name,
    symptom_keyword,
    triage_level,
    recommend_dept_id,
    special_weight,
    fast_track,
    enabled,
    deleted,
    version,
    created_time,
    updated_time
)
VALUES
    -- 急危重症：一级优先，急诊绿色通道
    ('RULE_EMERG_CHEST_PAIN', '胸痛急诊优先', '胸痛', 1, @dept_emergency, 100, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_DYSPNEA', '呼吸困难急诊优先', '呼吸困难', 1, @dept_emergency, 100, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_UNCONSCIOUS', '意识不清急诊优先', '意识不清', 1, @dept_emergency, 120, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_COMA', '昏迷急诊优先', '昏迷', 1, @dept_emergency, 120, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_CONVULSION', '抽搐急诊优先', '抽搐', 1, @dept_emergency, 110, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_SHOCK', '休克急诊优先', '休克', 1, @dept_emergency, 120, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_HEMORRHAGE', '大出血急诊优先', '大出血', 1, @dept_emergency, 120, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_STROKE', '偏瘫疑似卒中急诊优先', '偏瘫', 1, @dept_emergency, 110, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_HEMATEMESIS', '呕血急诊优先', '呕血', 1, @dept_emergency, 100, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_HEMOPTYSIS', '咯血急诊优先', '咯血', 1, @dept_emergency, 100, 1, 1, 0, 0, @seed_now, @seed_now),

    -- 急症：二级优先，需快速分流至急诊
    ('RULE_EMERG_TRAUMA', '外伤急诊分流', '外伤', 2, @dept_emergency, 80, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_FRACTURE', '骨折急诊分流', '骨折', 2, @dept_emergency, 80, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_HEAD_TRAUMA', '头部外伤急诊分流', '头部外伤', 2, @dept_emergency, 90, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_BURN', '烧伤急诊分流', '烧伤', 2, @dept_emergency, 85, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_POISONING', '中毒急诊分流', '中毒', 2, @dept_emergency, 90, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_ALLERGY', '严重过敏急诊分流', '过敏', 2, @dept_emergency, 75, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_SEVERE_ABDOMINAL_PAIN', '剧烈腹痛急诊分流', '剧烈腹痛', 2, @dept_emergency, 70, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_DEHYDRATION', '脱水急诊分流', '脱水', 2, @dept_emergency, 70, 1, 1, 0, 0, @seed_now, @seed_now),

    -- 儿科：儿童常见高频症状，当前简化模型中优先保证儿科分流可演示
    ('RULE_PED_FEVER', '儿童发热儿科优先', '发热', 3, @dept_pediatrics, 35, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_COUGH', '儿童咳嗽儿科优先', '咳嗽', 3, @dept_pediatrics, 25, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_VOMITING', '儿童呕吐儿科优先', '呕吐', 3, @dept_pediatrics, 30, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_DIARRHEA', '儿童腹泻儿科优先', '腹泻', 3, @dept_pediatrics, 30, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_HAND_FOOT_MOUTH', '手足口病儿科优先', '手足口', 2, @dept_pediatrics, 50, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_WHEEZE', '喘息儿科快速分诊', '喘息', 2, @dept_pediatrics, 55, 1, 1, 0, 0, @seed_now, @seed_now),

    -- 妇产科：孕产妇优先与专科分流
    ('RULE_OBS_PREGNANT', '孕产妇绿色通道', '孕', 2, @dept_obstetrics, 90, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_OBS_BLEEDING', '孕期出血妇产急办', '阴道出血', 1, @dept_obstetrics, 110, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_OBS_SEE_RED', '见红妇产急办', '见红', 2, @dept_obstetrics, 90, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_OBS_RUPTURE', '破水妇产急办', '破水', 2, @dept_obstetrics, 95, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_OBS_CONTRACTION', '规律宫缩妇产优先', '宫缩', 2, @dept_obstetrics, 85, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_OBS_ABORTION', '先兆流产妇产优先', '先兆流产', 2, @dept_obstetrics, 95, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_OBS_FETAL_MOVEMENT', '胎动减少妇产优先', '胎动减少', 2, @dept_obstetrics, 95, 1, 1, 0, 0, @seed_now, @seed_now),

    -- 全科门诊：非急危重且常见门诊主诉
    ('RULE_GEN_DIZZINESS', '头晕全科分流', '头晕', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_FATIGUE', '乏力全科分流', '乏力', 4, @dept_general, 15, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_SORE_THROAT', '咽痛全科分流', '咽痛', 4, @dept_general, 15, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_RASH', '皮疹全科分流', '皮疹', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_HYPERTENSION', '血压升高全科分流', '血压高', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_PALPITATION', '心悸全科分流', '心悸', 3, @dept_general, 35, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_ABDOMINAL_PAIN', '普通腹痛全科分流', '腹痛', 3, @dept_general, 35, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_HEADACHE', '头痛全科分流', '头痛', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_SKIN_ITCHING', '皮肤瘙痒全科分流', '皮肤瘙痒', 4, @dept_general, 10, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_REVISIT', '复诊患者全科分流', '复诊', 4, @dept_general, 10, 0, 1, 0, 0, @seed_now, @seed_now)
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    symptom_keyword = VALUES(symptom_keyword),
    triage_level = VALUES(triage_level),
    recommend_dept_id = VALUES(recommend_dept_id),
    special_weight = VALUES(special_weight),
    fast_track = VALUES(fast_track),
    enabled = VALUES(enabled),
    deleted = VALUES(deleted),
    version = VALUES(version),
    updated_time = VALUES(updated_time);

COMMIT;

-- 可选检查
SELECT rule_code, rule_name, symptom_keyword, triage_level, recommend_dept_id, special_weight, fast_track, enabled
FROM triage_rule
ORDER BY triage_level ASC, recommend_dept_id ASC, special_weight DESC, rule_code ASC;
