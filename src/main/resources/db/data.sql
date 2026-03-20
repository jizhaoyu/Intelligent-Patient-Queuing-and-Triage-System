SET @seed_now = NOW();
SET @seed_date = DATE_FORMAT(@seed_now, '%Y%m%d');

-- 基础字典数据
INSERT INTO clinic_dept (id, dept_code, dept_name, enabled, deleted, version)
VALUES (1, 'EMERGENCY', '急诊科', 1, 0, 0),
       (2, 'PEDIATRICS', '儿科', 1, 0, 0),
       (3, 'OBSTETRICS', '妇产科', 1, 0, 0),
       (4, 'GENERAL', '全科门诊', 1, 0, 0)
ON DUPLICATE KEY UPDATE
       dept_code = VALUES(dept_code),
       dept_name = VALUES(dept_name),
       enabled = VALUES(enabled),
       deleted = VALUES(deleted),
       version = VALUES(version);

INSERT INTO clinic_room (id, dept_id, room_code, room_name, doctor_name, enabled, deleted, version)
VALUES (1, 1, 'EMG-01', '急诊1诊室', '张医生', 1, 0, 0),
       (2, 1, 'EMG-02', '急诊2诊室', '陈医生', 1, 0, 0),
       (3, 2, 'PED-01', '儿科1诊室', '李医生', 1, 0, 0),
       (4, 2, 'PED-02', '儿科2诊室', '周医生', 1, 0, 0),
       (5, 3, 'OBS-01', '妇产1诊室', '王医生', 1, 0, 0),
       (6, 3, 'OBS-02', '妇产2诊室', '孙医生', 1, 0, 0),
       (7, 4, 'GEN-01', '全科1诊室', '赵医生', 1, 0, 0),
       (8, 4, 'GEN-02', '全科2诊室', '刘医生', 1, 0, 0)
ON DUPLICATE KEY UPDATE
       dept_id = VALUES(dept_id),
       room_code = VALUES(room_code),
       room_name = VALUES(room_name),
       doctor_name = VALUES(doctor_name),
       enabled = VALUES(enabled),
       deleted = VALUES(deleted),
       version = VALUES(version);

INSERT INTO triage_rule (id, rule_code, rule_name, symptom_keyword, triage_level, recommend_dept_id, special_weight, fast_track, enabled, deleted, version)
VALUES (1, 'RULE_CHEST', '胸痛急诊优先', '胸痛', 1, 1, 60, 1, 1, 0, 0),
       (2, 'RULE_CHILD', '儿童儿科优先', '发热', 3, 2, 20, 0, 1, 0, 0),
       (3, 'RULE_PREGNANT', '孕产妇绿色通道', '孕', 2, 3, 80, 1, 1, 0, 0),
       (4, 'RULE_BREATH', '呼吸困难急诊优先', '呼吸困难', 1, 1, 70, 1, 1, 0, 0),
       (5, 'RULE_TRAUMA', '外伤急诊分流', '外伤', 2, 1, 40, 1, 1, 0, 0)
ON DUPLICATE KEY UPDATE
       rule_code = VALUES(rule_code),
       rule_name = VALUES(rule_name),
       symptom_keyword = VALUES(symptom_keyword),
       triage_level = VALUES(triage_level),
       recommend_dept_id = VALUES(recommend_dept_id),
       special_weight = VALUES(special_weight),
       fast_track = VALUES(fast_track),
       enabled = VALUES(enabled),
       deleted = VALUES(deleted),
       version = VALUES(version);

-- 用户、角色与权限
INSERT INTO sys_role (id, role_code, role_name, deleted, version)
VALUES (1, 'ADMIN', '管理员', 0, 0),
       (2, 'TRIAGE_NURSE', '分诊护士', 0, 0),
       (3, 'DOCTOR', '医生', 0, 0),
       (4, 'GUIDE_DESK', '导诊台', 0, 0)
ON DUPLICATE KEY UPDATE
       role_code = VALUES(role_code),
       role_name = VALUES(role_name),
       deleted = VALUES(deleted),
       version = VALUES(version);

INSERT INTO sys_permission (id, permission_code, permission_name, deleted, version)
VALUES (1, 'patient:manage', '患者档案管理', 0, 0),
       (2, 'visit:manage', '到诊登记管理', 0, 0),
       (3, 'triage:assess', '分诊评估', 0, 0),
       (4, 'triage:rule', '分诊规则维护', 0, 0),
       (5, 'queue:manage', '排队管理', 0, 0),
       (6, 'queue:call', '叫号操作', 0, 0),
       (7, 'dashboard:view', '看板查看', 0, 0)
ON DUPLICATE KEY UPDATE
       permission_code = VALUES(permission_code),
       permission_name = VALUES(permission_name),
       deleted = VALUES(deleted),
       version = VALUES(version);

INSERT INTO sys_user (id, username, password, nickname, role_code, dept_id, room_id, enabled, deleted, version)
VALUES (1, 'admin', '$2a$10$5HogmhBu0Uk6jLZi6rAGg.bLqA29I4vCqnDJEtGosqEcstVnNwt2C', '系统管理员', 'ADMIN', NULL, NULL, 1, 0, 0),
       (2, 'triage.nurse', '$2a$10$5HogmhBu0Uk6jLZi6rAGg.bLqA29I4vCqnDJEtGosqEcstVnNwt2C', '分诊护士', 'TRIAGE_NURSE', 1, NULL, 1, 0, 0),
       (3, 'doctor.zhang', '$2a$10$5HogmhBu0Uk6jLZi6rAGg.bLqA29I4vCqnDJEtGosqEcstVnNwt2C', '张医生', 'DOCTOR', 1, 1, 1, 0, 0),
       (4, 'guide.desk', '$2a$10$5HogmhBu0Uk6jLZi6rAGg.bLqA29I4vCqnDJEtGosqEcstVnNwt2C', '导诊台', 'GUIDE_DESK', 4, NULL, 1, 0, 0)
ON DUPLICATE KEY UPDATE
       username = VALUES(username),
       password = VALUES(password),
       nickname = VALUES(nickname),
       role_code = VALUES(role_code),
       dept_id = VALUES(dept_id),
       room_id = VALUES(room_id),
       enabled = VALUES(enabled),
       deleted = VALUES(deleted),
       version = VALUES(version);

INSERT INTO sys_user_role (id, user_id, role_id, created_time)
VALUES (1, 1, 1, @seed_now),
       (2, 2, 2, @seed_now),
       (3, 3, 3, @seed_now),
       (4, 4, 4, @seed_now)
ON DUPLICATE KEY UPDATE
       user_id = VALUES(user_id),
       role_id = VALUES(role_id),
       created_time = VALUES(created_time);

INSERT INTO sys_role_permission (id, role_id, permission_id, created_time)
VALUES (1, 1, 1, @seed_now),
       (2, 1, 2, @seed_now),
       (3, 1, 3, @seed_now),
       (4, 1, 4, @seed_now),
       (5, 1, 5, @seed_now),
       (6, 1, 6, @seed_now),
       (7, 1, 7, @seed_now),
       (8, 2, 1, @seed_now),
       (9, 2, 2, @seed_now),
       (10, 2, 3, @seed_now),
       (11, 2, 5, @seed_now),
       (12, 2, 7, @seed_now),
       (13, 3, 6, @seed_now),
       (14, 3, 7, @seed_now),
       (15, 4, 1, @seed_now),
       (16, 4, 2, @seed_now),
       (17, 4, 7, @seed_now)
ON DUPLICATE KEY UPDATE
       role_id = VALUES(role_id),
       permission_id = VALUES(permission_id),
       created_time = VALUES(created_time);

-- 患者与就诊数据
INSERT INTO patient_info (id, patient_no, patient_name, gender, birth_date, phone, id_card, allergy_history, special_tags, deleted, version, created_time, updated_time)
VALUES (1, CONCAT('P', @seed_date, '001'), '陈建国', 'MALE', '1978-04-12', '13800010001', '110101197804120011', '青霉素', '高血压,复诊', 0, 0, DATE_SUB(@seed_now, INTERVAL 70 MINUTE), DATE_SUB(@seed_now, INTERVAL 70 MINUTE)),
       (2, CONCAT('P', @seed_date, '002'), '王晓宁', 'FEMALE', '1989-11-23', '13800010002', '110101198911230028', '无', '哮喘史', 0, 0, DATE_SUB(@seed_now, INTERVAL 48 MINUTE), DATE_SUB(@seed_now, INTERVAL 48 MINUTE)),
       (3, CONCAT('P', @seed_date, '003'), '李梓涵', 'MALE', '2020-06-18', '13800010003', '110101202006180035', '头孢过敏', '儿童', 0, 0, DATE_SUB(@seed_now, INTERVAL 65 MINUTE), DATE_SUB(@seed_now, INTERVAL 65 MINUTE)),
       (4, CONCAT('P', @seed_date, '004'), '赵雨桐', 'FEMALE', '1994-03-08', '13800010004', '110101199403080042', '无', '孕妇,重点关注', 0, 0, DATE_SUB(@seed_now, INTERVAL 45 MINUTE), DATE_SUB(@seed_now, INTERVAL 45 MINUTE)),
       (5, CONCAT('P', @seed_date, '005'), '孙海峰', 'MALE', '1965-09-17', '13800010005', '110101196509170059', '碘造影剂', '老年,复诊', 0, 0, DATE_SUB(@seed_now, INTERVAL 58 MINUTE), DATE_SUB(@seed_now, INTERVAL 58 MINUTE)),
       (6, CONCAT('P', @seed_date, '006'), '周敏', 'FEMALE', '1997-01-05', '13800010006', '110101199701050066', '海鲜', '首次建档', 0, 0, DATE_SUB(@seed_now, INTERVAL 20 MINUTE), DATE_SUB(@seed_now, INTERVAL 20 MINUTE)),
       (7, CONCAT('P', @seed_date, '007'), '吴国强', 'MALE', '1958-12-01', '13800010007', '110101195812010073', '无', '老年', 0, 0, DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 18 MINUTE)),
       (8, CONCAT('P', @seed_date, '008'), '郑思雨', 'FEMALE', '2001-07-14', '13800010008', '110101200107140080', '无', '普通门诊', 0, 0, DATE_SUB(@seed_now, INTERVAL 32 MINUTE), DATE_SUB(@seed_now, INTERVAL 32 MINUTE)),
       (9, CONCAT('P', @seed_date, '009'), '刘晨', 'MALE', '2008-02-29', '13800010009', '110101200802290097', '无', '学生', 0, 0, DATE_SUB(@seed_now, INTERVAL 24 MINUTE), DATE_SUB(@seed_now, INTERVAL 24 MINUTE)),
       (10, CONCAT('P', @seed_date, '010'), '黄可欣', 'FEMALE', '2018-10-09', '13800010010', '110101201810090104', '花粉', '儿童', 0, 0, DATE_SUB(@seed_now, INTERVAL 30 MINUTE), DATE_SUB(@seed_now, INTERVAL 30 MINUTE)),
       (11, CONCAT('P', @seed_date, '011'), '何静', 'FEMALE', '1972-05-21', '13800010011', '110101197205210111', '无', '老年,复诊,行动不便', 0, 0, DATE_SUB(@seed_now, INTERVAL 42 MINUTE), DATE_SUB(@seed_now, INTERVAL 42 MINUTE)),
       (12, CONCAT('P', @seed_date, '012'), '林美玲', 'FEMALE', '1990-08-30', '13800010012', '110101199008300128', '无', '孕妇', 0, 0, DATE_SUB(@seed_now, INTERVAL 26 MINUTE), DATE_SUB(@seed_now, INTERVAL 26 MINUTE))
ON DUPLICATE KEY UPDATE
       patient_no = VALUES(patient_no),
       patient_name = VALUES(patient_name),
       gender = VALUES(gender),
       birth_date = VALUES(birth_date),
       phone = VALUES(phone),
       id_card = VALUES(id_card),
       allergy_history = VALUES(allergy_history),
       special_tags = VALUES(special_tags),
       deleted = VALUES(deleted),
       version = VALUES(version),
       created_time = VALUES(created_time),
       updated_time = VALUES(updated_time);

INSERT INTO visit_record (id, patient_id, visit_no, status, register_time, arrival_time, chief_complaint, current_dept_id, current_room_id, deleted, version, created_time, updated_time)
VALUES (1, 1, CONCAT('V', @seed_date, '001'), 'QUEUING', DATE_SUB(@seed_now, INTERVAL 70 MINUTE), DATE_SUB(@seed_now, INTERVAL 55 MINUTE), '突发胸痛伴出汗', 1, 2, 0, 0, DATE_SUB(@seed_now, INTERVAL 70 MINUTE), DATE_SUB(@seed_now, INTERVAL 40 MINUTE)),
       (2, 2, CONCAT('V', @seed_date, '002'), 'IN_TREATMENT', DATE_SUB(@seed_now, INTERVAL 48 MINUTE), DATE_SUB(@seed_now, INTERVAL 40 MINUTE), '胸闷伴呼吸困难', 1, 1, 0, 0, DATE_SUB(@seed_now, INTERVAL 48 MINUTE), DATE_SUB(@seed_now, INTERVAL 6 MINUTE)),
       (3, 3, CONCAT('V', @seed_date, '003'), 'QUEUING', DATE_SUB(@seed_now, INTERVAL 65 MINUTE), DATE_SUB(@seed_now, INTERVAL 52 MINUTE), '高热伴咳嗽', 2, 3, 0, 0, DATE_SUB(@seed_now, INTERVAL 65 MINUTE), DATE_SUB(@seed_now, INTERVAL 22 MINUTE)),
       (4, 4, CONCAT('V', @seed_date, '004'), 'COMPLETED', DATE_SUB(@seed_now, INTERVAL 45 MINUTE), DATE_SUB(@seed_now, INTERVAL 38 MINUTE), '孕28周腹痛', 3, 5, 0, 0, DATE_SUB(@seed_now, INTERVAL 45 MINUTE), DATE_SUB(@seed_now, INTERVAL 5 MINUTE)),
       (5, 5, CONCAT('V', @seed_date, '005'), 'COMPLETED', DATE_SUB(@seed_now, INTERVAL 58 MINUTE), DATE_SUB(@seed_now, INTERVAL 50 MINUTE), '头晕伴血压波动', 4, 7, 0, 0, DATE_SUB(@seed_now, INTERVAL 58 MINUTE), DATE_SUB(@seed_now, INTERVAL 2 MINUTE)),
       (6, 6, CONCAT('V', @seed_date, '006'), 'REGISTERED', DATE_SUB(@seed_now, INTERVAL 20 MINUTE), NULL, '皮肤瘙痒待就诊', NULL, NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 20 MINUTE), DATE_SUB(@seed_now, INTERVAL 20 MINUTE)),
       (7, 7, CONCAT('V', @seed_date, '007'), 'ARRIVED', DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 10 MINUTE), '咳嗽乏力三天', NULL, NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 10 MINUTE)),
       (8, 8, CONCAT('V', @seed_date, '008'), 'TRIAGED', DATE_SUB(@seed_now, INTERVAL 32 MINUTE), DATE_SUB(@seed_now, INTERVAL 25 MINUTE), '咽痛伴低热', 4, NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 32 MINUTE), DATE_SUB(@seed_now, INTERVAL 18 MINUTE)),
       (9, 9, CONCAT('V', @seed_date, '009'), 'CANCELLED', DATE_SUB(@seed_now, INTERVAL 24 MINUTE), DATE_SUB(@seed_now, INTERVAL 22 MINUTE), '前臂外伤疼痛', 1, 2, 0, 0, DATE_SUB(@seed_now, INTERVAL 24 MINUTE), DATE_SUB(@seed_now, INTERVAL 4 MINUTE)),
       (10, 10, CONCAT('V', @seed_date, '010'), 'IN_TREATMENT', DATE_SUB(@seed_now, INTERVAL 30 MINUTE), DATE_SUB(@seed_now, INTERVAL 28 MINUTE), '高热咳嗽', 2, 4, 0, 0, DATE_SUB(@seed_now, INTERVAL 30 MINUTE), DATE_SUB(@seed_now, INTERVAL 7 MINUTE)),
       (11, 11, CONCAT('V', @seed_date, '011'), 'QUEUING', DATE_SUB(@seed_now, INTERVAL 42 MINUTE), DATE_SUB(@seed_now, INTERVAL 37 MINUTE), '头晕伴乏力', 4, 8, 0, 0, DATE_SUB(@seed_now, INTERVAL 42 MINUTE), DATE_SUB(@seed_now, INTERVAL 34 MINUTE)),
       (12, 12, CONCAT('V', @seed_date, '012'), 'IN_TREATMENT', DATE_SUB(@seed_now, INTERVAL 26 MINUTE), DATE_SUB(@seed_now, INTERVAL 24 MINUTE), '孕早期出血', 3, 6, 0, 0, DATE_SUB(@seed_now, INTERVAL 26 MINUTE), DATE_SUB(@seed_now, INTERVAL 9 MINUTE))
ON DUPLICATE KEY UPDATE
       patient_id = VALUES(patient_id),
       visit_no = VALUES(visit_no),
       status = VALUES(status),
       register_time = VALUES(register_time),
       arrival_time = VALUES(arrival_time),
       chief_complaint = VALUES(chief_complaint),
       current_dept_id = VALUES(current_dept_id),
       current_room_id = VALUES(current_room_id),
       deleted = VALUES(deleted),
       version = VALUES(version),
       created_time = VALUES(created_time),
       updated_time = VALUES(updated_time);

INSERT INTO triage_assessment (id, visit_id, symptom_tags, body_temperature, heart_rate, blood_pressure, blood_oxygen, triage_level, recommend_dept_id, priority_score, fast_track, manual_adjust_score, assessor, assessed_time, deleted, version, created_time, updated_time)
VALUES (1, 1, '胸痛,出汗', 36.80, 108, '150/95', 93, 1, 1, 1170, 1, 0, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 50 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 50 MINUTE), DATE_SUB(@seed_now, INTERVAL 50 MINUTE)),
       (2, 2, '呼吸困难,胸闷', 37.10, 118, '145/90', 88, 1, 1, 1170, 1, 20, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 35 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 35 MINUTE), DATE_SUB(@seed_now, INTERVAL 35 MINUTE)),
       (3, 3, '发热,咳嗽', 39.80, 132, '102/68', 97, 2, 2, 884, 1, 0, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 46 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 46 MINUTE), DATE_SUB(@seed_now, INTERVAL 46 MINUTE)),
       (4, 4, '孕28周,腹痛', 37.20, 102, '118/76', 99, 2, 3, 946, 1, 10, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 34 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 34 MINUTE), DATE_SUB(@seed_now, INTERVAL 34 MINUTE)),
       (5, 5, '头晕,血压波动', 36.70, 92, '168/102', 98, 4, 4, 290, 0, 30, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 44 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 44 MINUTE), DATE_SUB(@seed_now, INTERVAL 44 MINUTE)),
       (6, 8, '咽痛,低热', 37.80, 88, '112/72', 99, 4, 4, 150, 0, 0, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 18 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 18 MINUTE)),
       (7, 9, '前臂外伤,疼痛', 36.50, 98, '124/79', 99, 2, 1, 784, 1, 0, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 19 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 19 MINUTE), DATE_SUB(@seed_now, INTERVAL 19 MINUTE)),
       (8, 10, '发热,咳嗽', 39.60, 136, '101/66', 98, 2, 2, 846, 1, 10, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 23 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 23 MINUTE), DATE_SUB(@seed_now, INTERVAL 23 MINUTE)),
       (9, 11, '头晕,乏力', 36.40, 146, '156/96', 97, 2, 4, 914, 1, 30, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 32 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 32 MINUTE), DATE_SUB(@seed_now, INTERVAL 32 MINUTE)),
       (10, 12, '孕早期出血', 36.90, 104, '110/72', 99, 2, 3, 928, 1, 20, 'triage.nurse', DATE_SUB(@seed_now, INTERVAL 20 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 20 MINUTE), DATE_SUB(@seed_now, INTERVAL 20 MINUTE))
ON DUPLICATE KEY UPDATE
       visit_id = VALUES(visit_id),
       symptom_tags = VALUES(symptom_tags),
       body_temperature = VALUES(body_temperature),
       heart_rate = VALUES(heart_rate),
       blood_pressure = VALUES(blood_pressure),
       blood_oxygen = VALUES(blood_oxygen),
       triage_level = VALUES(triage_level),
       recommend_dept_id = VALUES(recommend_dept_id),
       priority_score = VALUES(priority_score),
       fast_track = VALUES(fast_track),
       manual_adjust_score = VALUES(manual_adjust_score),
       assessor = VALUES(assessor),
       assessed_time = VALUES(assessed_time),
       deleted = VALUES(deleted),
       version = VALUES(version),
       created_time = VALUES(created_time),
       updated_time = VALUES(updated_time);

INSERT INTO queue_ticket (id, ticket_no, visit_id, patient_id, assessment_id, dept_id, room_id, triage_level, priority_score, status, recall_count, fast_track, enqueue_time, call_time, complete_time, deleted, version, created_time, updated_time)
VALUES (1, CONCAT(@seed_date, '-1-0001'), 1, 1, 1, 1, 2, 1, 1170, 'WAITING', 0, 1, DATE_SUB(@seed_now, INTERVAL 40 MINUTE), NULL, NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 40 MINUTE), DATE_SUB(@seed_now, INTERVAL 40 MINUTE)),
       (2, CONCAT(@seed_date, '-1-0002'), 2, 2, 2, 1, 1, 1, 1170, 'CALLING', 0, 1, DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 6 MINUTE), NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 6 MINUTE)),
       (3, CONCAT(@seed_date, '-2-0001'), 3, 3, 3, 2, 3, 2, 884, 'WAITING', 0, 1, DATE_SUB(@seed_now, INTERVAL 22 MINUTE), NULL, NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 22 MINUTE), DATE_SUB(@seed_now, INTERVAL 22 MINUTE)),
       (4, CONCAT(@seed_date, '-3-0001'), 4, 4, 4, 3, 5, 2, 946, 'COMPLETED', 0, 1, DATE_SUB(@seed_now, INTERVAL 26 MINUTE), DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 5 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 26 MINUTE), DATE_SUB(@seed_now, INTERVAL 5 MINUTE)),
       (5, CONCAT(@seed_date, '-4-0001'), 5, 5, 5, 4, 7, 4, 290, 'COMPLETED', 0, 0, DATE_SUB(@seed_now, INTERVAL 28 MINUTE), DATE_SUB(@seed_now, INTERVAL 13 MINUTE), DATE_SUB(@seed_now, INTERVAL 2 MINUTE), 0, 0, DATE_SUB(@seed_now, INTERVAL 28 MINUTE), DATE_SUB(@seed_now, INTERVAL 2 MINUTE)),
       (6, CONCAT(@seed_date, '-1-0003'), 9, 9, 7, 1, 2, 2, 784, 'CANCELLED', 0, 1, DATE_SUB(@seed_now, INTERVAL 12 MINUTE), NULL, NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 12 MINUTE), DATE_SUB(@seed_now, INTERVAL 4 MINUTE)),
       (7, CONCAT(@seed_date, '-2-0002'), 10, 10, 8, 2, 4, 2, 846, 'MISSED', 0, 1, DATE_SUB(@seed_now, INTERVAL 16 MINUTE), DATE_SUB(@seed_now, INTERVAL 7 MINUTE), NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 16 MINUTE), DATE_SUB(@seed_now, INTERVAL 2 MINUTE)),
       (8, CONCAT(@seed_date, '-4-0002'), 11, 11, 9, 4, 8, 2, 914, 'WAITING', 0, 1, DATE_SUB(@seed_now, INTERVAL 34 MINUTE), NULL, NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 34 MINUTE), DATE_SUB(@seed_now, INTERVAL 31 MINUTE)),
       (9, CONCAT(@seed_date, '-3-0002'), 12, 12, 10, 3, 6, 2, 928, 'CALLING', 1, 1, DATE_SUB(@seed_now, INTERVAL 14 MINUTE), DATE_SUB(@seed_now, INTERVAL 3 MINUTE), NULL, 0, 0, DATE_SUB(@seed_now, INTERVAL 14 MINUTE), DATE_SUB(@seed_now, INTERVAL 3 MINUTE))
ON DUPLICATE KEY UPDATE
       ticket_no = VALUES(ticket_no),
       visit_id = VALUES(visit_id),
       patient_id = VALUES(patient_id),
       assessment_id = VALUES(assessment_id),
       dept_id = VALUES(dept_id),
       room_id = VALUES(room_id),
       triage_level = VALUES(triage_level),
       priority_score = VALUES(priority_score),
       status = VALUES(status),
       recall_count = VALUES(recall_count),
       fast_track = VALUES(fast_track),
       enqueue_time = VALUES(enqueue_time),
       call_time = VALUES(call_time),
       complete_time = VALUES(complete_time),
       deleted = VALUES(deleted),
       version = VALUES(version),
       created_time = VALUES(created_time),
       updated_time = VALUES(updated_time);

INSERT INTO queue_event_log (id, ticket_no, event_type, from_status, to_status, room_id, operator_name, remark, deleted, version, created_time, updated_time)
VALUES (1, CONCAT(@seed_date, '-1-0001'), 'ENQUEUE', NULL, 'WAITING', 2, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 40 MINUTE), DATE_SUB(@seed_now, INTERVAL 40 MINUTE)),
       (2, CONCAT(@seed_date, '-1-0002'), 'ENQUEUE', NULL, 'WAITING', 1, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 18 MINUTE)),
       (3, CONCAT(@seed_date, '-1-0002'), 'CALL_NEXT', 'WAITING', 'CALLING', 1, 'doctor.zhang', '叫号', 0, 0, DATE_SUB(@seed_now, INTERVAL 6 MINUTE), DATE_SUB(@seed_now, INTERVAL 6 MINUTE)),
       (4, CONCAT(@seed_date, '-2-0001'), 'ENQUEUE', NULL, 'WAITING', 3, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 22 MINUTE), DATE_SUB(@seed_now, INTERVAL 22 MINUTE)),
       (5, CONCAT(@seed_date, '-3-0001'), 'ENQUEUE', NULL, 'WAITING', 5, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 26 MINUTE), DATE_SUB(@seed_now, INTERVAL 26 MINUTE)),
       (6, CONCAT(@seed_date, '-3-0001'), 'CALL_NEXT', 'WAITING', 'CALLING', 5, 'doctor.wang', '叫号', 0, 0, DATE_SUB(@seed_now, INTERVAL 18 MINUTE), DATE_SUB(@seed_now, INTERVAL 18 MINUTE)),
       (7, CONCAT(@seed_date, '-3-0001'), 'COMPLETE', 'CALLING', 'COMPLETED', 5, 'doctor.wang', '完成接诊', 0, 0, DATE_SUB(@seed_now, INTERVAL 5 MINUTE), DATE_SUB(@seed_now, INTERVAL 5 MINUTE)),
       (8, CONCAT(@seed_date, '-4-0001'), 'ENQUEUE', NULL, 'WAITING', 7, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 28 MINUTE), DATE_SUB(@seed_now, INTERVAL 28 MINUTE)),
       (9, CONCAT(@seed_date, '-4-0001'), 'CALL_NEXT', 'WAITING', 'CALLING', 7, 'doctor.zhao', '叫号', 0, 0, DATE_SUB(@seed_now, INTERVAL 13 MINUTE), DATE_SUB(@seed_now, INTERVAL 13 MINUTE)),
       (10, CONCAT(@seed_date, '-4-0001'), 'COMPLETE', 'CALLING', 'COMPLETED', 7, 'doctor.zhao', '完成接诊', 0, 0, DATE_SUB(@seed_now, INTERVAL 2 MINUTE), DATE_SUB(@seed_now, INTERVAL 2 MINUTE)),
       (11, CONCAT(@seed_date, '-1-0003'), 'ENQUEUE', NULL, 'WAITING', 2, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 12 MINUTE), DATE_SUB(@seed_now, INTERVAL 12 MINUTE)),
       (12, CONCAT(@seed_date, '-1-0003'), 'CANCEL', 'WAITING', 'CANCELLED', 2, 'guide.desk', '患者暂离取消排队', 0, 0, DATE_SUB(@seed_now, INTERVAL 4 MINUTE), DATE_SUB(@seed_now, INTERVAL 4 MINUTE)),
       (13, CONCAT(@seed_date, '-2-0002'), 'ENQUEUE', NULL, 'WAITING', 4, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 16 MINUTE), DATE_SUB(@seed_now, INTERVAL 16 MINUTE)),
       (14, CONCAT(@seed_date, '-2-0002'), 'CALL_NEXT', 'WAITING', 'CALLING', 4, 'doctor.zhou', '叫号', 0, 0, DATE_SUB(@seed_now, INTERVAL 7 MINUTE), DATE_SUB(@seed_now, INTERVAL 7 MINUTE)),
       (15, CONCAT(@seed_date, '-2-0002'), 'MISSED', 'CALLING', 'MISSED', 4, 'doctor.zhou', '过号', 0, 0, DATE_SUB(@seed_now, INTERVAL 2 MINUTE), DATE_SUB(@seed_now, INTERVAL 2 MINUTE)),
       (16, CONCAT(@seed_date, '-4-0002'), 'ENQUEUE', NULL, 'WAITING', 8, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 34 MINUTE), DATE_SUB(@seed_now, INTERVAL 34 MINUTE)),
       (17, CONCAT(@seed_date, '-4-0002'), 'MANUAL_ADJUST', 'WAITING', 'WAITING', 8, 'triage.nurse', '人工调高优先级30分', 0, 0, DATE_SUB(@seed_now, INTERVAL 31 MINUTE), DATE_SUB(@seed_now, INTERVAL 31 MINUTE)),
       (18, CONCAT(@seed_date, '-3-0002'), 'ENQUEUE', NULL, 'WAITING', 6, 'system', '入队', 0, 0, DATE_SUB(@seed_now, INTERVAL 14 MINUTE), DATE_SUB(@seed_now, INTERVAL 14 MINUTE)),
       (19, CONCAT(@seed_date, '-3-0002'), 'CALL_NEXT', 'WAITING', 'CALLING', 6, 'doctor.sun', '叫号', 0, 0, DATE_SUB(@seed_now, INTERVAL 9 MINUTE), DATE_SUB(@seed_now, INTERVAL 9 MINUTE)),
       (20, CONCAT(@seed_date, '-3-0002'), 'MISSED', 'CALLING', 'MISSED', 6, 'doctor.sun', '首次未到诊', 0, 0, DATE_SUB(@seed_now, INTERVAL 7 MINUTE), DATE_SUB(@seed_now, INTERVAL 7 MINUTE)),
       (21, CONCAT(@seed_date, '-3-0002'), 'RECALL', 'MISSED', 'CALLING', 6, 'doctor.sun', '复呼后重新接诊', 0, 0, DATE_SUB(@seed_now, INTERVAL 3 MINUTE), DATE_SUB(@seed_now, INTERVAL 3 MINUTE))
ON DUPLICATE KEY UPDATE
       ticket_no = VALUES(ticket_no),
       event_type = VALUES(event_type),
       from_status = VALUES(from_status),
       to_status = VALUES(to_status),
       room_id = VALUES(room_id),
       operator_name = VALUES(operator_name),
       remark = VALUES(remark),
       deleted = VALUES(deleted),
       version = VALUES(version),
       created_time = VALUES(created_time),
       updated_time = VALUES(updated_time);
