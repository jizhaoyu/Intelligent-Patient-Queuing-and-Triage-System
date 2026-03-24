START TRANSACTION;

SET @seed_now := NOW();
SET @dept_emergency := (SELECT id FROM clinic_dept WHERE dept_code = 'EMERGENCY' LIMIT 1);
SET @dept_pediatrics := (SELECT id FROM clinic_dept WHERE dept_code = 'PEDIATRICS' LIMIT 1);
SET @dept_obstetrics := (SELECT id FROM clinic_dept WHERE dept_code = 'OBSTETRICS' LIMIT 1);
SET @dept_general := (SELECT id FROM clinic_dept WHERE dept_code = 'GENERAL' LIMIT 1);
SET @dept_cardiology := (SELECT id FROM clinic_dept WHERE dept_code = 'CARDIOLOGY' LIMIT 1);
SET @dept_respiratory := (SELECT id FROM clinic_dept WHERE dept_code = 'RESPIRATORY' LIMIT 1);
SET @dept_neurology := (SELECT id FROM clinic_dept WHERE dept_code = 'NEUROLOGY' LIMIT 1);
SET @dept_orthopedics := (SELECT id FROM clinic_dept WHERE dept_code = 'ORTHOPEDICS' LIMIT 1);

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
    ('RULE_PED_FEVER', '儿童/小儿发热儿科优先', '儿童发热,小儿发热,宝宝发热,幼儿发热', 3, @dept_pediatrics, 35, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_COUGH', '儿童/小儿咳嗽儿科优先', '儿童咳嗽,小儿咳嗽,宝宝咳嗽,幼儿咳嗽', 3, @dept_pediatrics, 25, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_VOMITING', '儿童/小儿呕吐儿科优先', '儿童呕吐,小儿呕吐,宝宝呕吐,幼儿呕吐', 3, @dept_pediatrics, 30, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_DIARRHEA', '儿童/小儿腹泻儿科优先', '儿童腹泻,小儿腹泻,宝宝腹泻,幼儿腹泻', 3, @dept_pediatrics, 30, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_HAND_FOOT_MOUTH', '手足口病儿科优先', '手足口,手足口病,儿童手足口,小儿手足口', 2, @dept_pediatrics, 50, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_PED_WHEEZE', '儿童喘息儿科快速分诊', '儿童喘息,小儿喘息,宝宝喘息,儿童喘憋,小儿喘憋', 2, @dept_pediatrics, 55, 1, 1, 0, 0, @seed_now, @seed_now),

    ('RULE_EMERG_ALLERGY', '严重过敏急诊分流', '严重过敏,急性过敏,过敏反应', 2, @dept_emergency, 75, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_POISONING', '中毒急诊分流', '中毒,误服', 2, @dept_emergency, 90, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_SEVERE_ABDOMINAL_PAIN', '剧烈腹痛急诊分流', '剧烈腹痛,腹部剧痛', 2, @dept_emergency, 70, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_CHEST_TIGHTNESS', '胸闷胸痛急诊优先', '胸闷,胸口发紧,胸前区疼痛,压榨性胸痛', 1, @dept_emergency, 105, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_ACUTE_DYSPNEA', '急性气促急诊优先', '呼吸急促,气短,喘不上气,呼吸窘迫', 1, @dept_emergency, 105, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_SYNCOPE', '晕厥急诊优先', '晕厥,昏倒,突然晕倒', 1, @dept_emergency, 110, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_CONFUSION', '意识模糊急诊优先', '意识模糊,言语不清,答非所问', 1, @dept_emergency, 110, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_HEMIPARESIS', '急性神经缺损急诊优先', '一侧无力,口角歪斜,单侧肢体无力', 1, @dept_emergency, 112, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_ANAPHYLACTIC_SHOCK', '过敏性休克急诊优先', '过敏性休克,喉头水肿', 1, @dept_emergency, 118, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_GI_BLEED', '消化道出血急诊优先', '黑便,便血,柏油样便', 1, @dept_emergency, 102, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_ASTHMA_ATTACK', '喘憋急诊优先', '喘憋,哮喘发作', 1, @dept_emergency, 104, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_RIGHT_LOWER_ABDOMINAL_PAIN', '右下腹痛急诊分流', '右下腹痛,反跳痛', 2, @dept_emergency, 88, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_EXPLOSIVE_HEADACHE', '剧烈头痛急诊分流', '剧烈头痛,爆炸样头痛', 2, @dept_emergency, 90, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_TRAFFIC_ACCIDENT', '车祸伤急诊分流', '车祸伤,车祸外伤', 2, @dept_emergency, 95, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_KNIFE_WOUND', '刀刺伤急诊分流', '刀伤,刺伤', 2, @dept_emergency, 96, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_FALL_INJURY', '坠落摔伤急诊分流', '坠落伤,摔伤', 2, @dept_emergency, 92, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_DISLOCATION', '脱位急诊分流', '脱位', 2, @dept_emergency, 84, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_SCALD', '烫伤急诊分流', '烫伤', 2, @dept_emergency, 84, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_DRUG_POISONING', '药物毒物暴露急诊分流', '药物中毒,农药中毒,酒精中毒', 2, @dept_emergency, 95, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_HIGH_FEVER', '高热寒战急诊分流', '高热,寒战,持续高热', 2, @dept_emergency, 82, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_SEVERE_PALPITATION', '心慌胸闷急诊分流', '心悸胸闷,心慌胸闷', 2, @dept_emergency, 86, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_FREQUENT_VOMITING', '频繁呕吐急诊分流', '频繁呕吐,喷射性呕吐', 2, @dept_emergency, 80, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_SEVERE_DIARRHEA', '腹泻脱水急诊分流', '腹泻脱水,频繁腹泻', 2, @dept_emergency, 78, 1, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_PERSISTENT_PALPITATION', '持续心悸急诊观察', '持续心悸,阵发性心慌', 3, @dept_emergency, 58, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_DIZZINESS_NAUSEA', '头晕伴恶心急诊观察', '头晕伴恶心,眩晕伴恶心', 3, @dept_emergency, 50, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_PERSISTENT_HEADACHE', '持续头痛急诊观察', '持续头痛伴恶心,偏头痛发作', 3, @dept_emergency, 48, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_MODERATE_ABDOMINAL_DISCOMFORT', '腹部不适急诊观察', '腹部隐痛,中上腹不适', 3, @dept_emergency, 46, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_MILD_UPPER_RESPIRATORY', '鼻塞流涕急诊普通分诊', '鼻塞流涕,咽干鼻塞', 4, @dept_emergency, 24, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_MILD_THROAT_DISCOMFORT', '轻度咽痛急诊普通分诊', '轻度咽痛,咽部异物感', 4, @dept_emergency, 22, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_MILD_HEADACHE', '轻度头痛急诊普通分诊', '轻度头痛,头胀不适', 4, @dept_emergency, 22, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_MILD_DIZZINESS', '轻度头晕急诊普通分诊', '轻度头晕,短暂眩晕', 4, @dept_emergency, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_MILD_GASTRO', '轻度胃肠不适急诊普通分诊', '轻度恶心,偶发腹泻', 4, @dept_emergency, 18, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_EMERG_MUSCLE_PAIN', '肌肉酸痛急诊普通分诊', '肌肉酸痛,四肢酸痛', 4, @dept_emergency, 16, 0, 1, 0, 0, @seed_now, @seed_now),

    ('RULE_CARD_EXERTIONAL_CHEST_TIGHTNESS', '活动后胸闷心内科分流', '活动后胸闷,劳累后胸闷', 3, @dept_cardiology, 42, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_CARD_INTERMITTENT_PALPITATION', '间断心悸心内科分流', '间断心悸,偶发心慌', 3, @dept_cardiology, 42, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_CARD_PRECORDIAL_DISCOMFORT', '心前区不适心内科分流', '心前区不适,胸口隐痛', 4, @dept_cardiology, 28, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_CARD_BP_FLUCTUATION', '血压波动心内科分流', '血压波动,反复血压升高', 4, @dept_cardiology, 26, 0, 1, 0, 0, @seed_now, @seed_now),

    ('RULE_RESP_PERSISTENT_COUGH', '持续咳嗽呼吸内科分流', '持续咳嗽,反复咳嗽', 3, @dept_respiratory, 38, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_RESP_COUGH_WITH_SPUTUM', '咳嗽咳痰呼吸内科分流', '咳嗽咳痰,黄痰咳嗽', 3, @dept_respiratory, 36, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_RESP_NOCTURNAL_COUGH', '夜间咳嗽呼吸内科分流', '夜间咳嗽,晨起咳痰', 4, @dept_respiratory, 24, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_RESP_ALLERGIC_COUGH', '过敏性咳嗽呼吸内科分流', '过敏性咳嗽,咽痒干咳', 4, @dept_respiratory, 24, 0, 1, 0, 0, @seed_now, @seed_now),

    ('RULE_NEU_RECURRENT_HEADACHE', '反复头痛神经内科分流', '反复头痛,慢性头痛', 3, @dept_neurology, 36, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_NEU_VERTIGO_TINNITUS', '眩晕耳鸣神经内科分流', '眩晕耳鸣,头晕耳鸣', 3, @dept_neurology, 34, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_NEU_LIMB_NUMBNESS', '肢体麻木神经内科分流', '手脚发麻,肢体麻木', 3, @dept_neurology, 34, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_NEU_TREMOR', '震颤神经内科分流', '手抖震颤,静止性震颤', 4, @dept_neurology, 22, 0, 1, 0, 0, @seed_now, @seed_now),

    ('RULE_ORT_LUMBAR_STRAIN', '腰背酸痛骨科分流', '腰肌劳损,腰背酸痛', 3, @dept_orthopedics, 34, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_ORT_ANKLE_SPRAIN', '踝关节扭伤骨科分流', '脚踝扭伤,踝关节扭伤', 3, @dept_orthopedics, 34, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_ORT_KNEE_JOINT_PAIN', '膝关节痛骨科分流', '膝关节痛,膝盖酸痛', 4, @dept_orthopedics, 22, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_ORT_NECK_SHOULDER_PAIN', '颈肩疼痛骨科分流', '颈肩疼痛,肩颈酸痛', 4, @dept_orthopedics, 22, 0, 1, 0, 0, @seed_now, @seed_now),

    ('RULE_GEN_DIZZINESS', '头晕全科分流', '头晕,眩晕', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_FATIGUE', '乏力全科分流', '乏力,疲劳', 4, @dept_general, 15, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_SORE_THROAT', '咽痛全科分流', '咽痛,喉咙痛', 4, @dept_general, 15, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_RASH', '皮疹全科分流', '皮疹,红疹', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_HYPERTENSION', '血压升高全科分流', '血压高,血压升高', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_PALPITATION', '心悸全科分流', '心悸,心慌', 3, @dept_general, 35, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_ABDOMINAL_PAIN', '普通腹痛全科分流', '腹痛,肚子痛', 3, @dept_general, 35, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_HEADACHE', '头痛全科分流', '头痛,头胀', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_SKIN_ITCHING', '皮肤瘙痒全科分流', '皮肤瘙痒,皮肤发痒', 4, @dept_general, 10, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_REVISIT', '复诊患者全科分流', '复诊,复查', 4, @dept_general, 10, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_FEVER', '发热全科分流', '发热', 4, @dept_general, 18, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_COUGH', '咳嗽全科分流', '咳嗽', 4, @dept_general, 18, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_VOMITING', '呕吐全科分流', '呕吐', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_DIARRHEA', '腹泻全科分流', '腹泻', 4, @dept_general, 20, 0, 1, 0, 0, @seed_now, @seed_now),
    ('RULE_GEN_NAUSEA', '恶心全科分流', '恶心', 4, @dept_general, 12, 0, 1, 0, 0, @seed_now, @seed_now)
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
