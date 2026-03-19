INSERT INTO clinic_dept (id, dept_code, dept_name, enabled, deleted, version)
VALUES (1, 'EMERGENCY', '急诊科', 1, 0, 0),
       (2, 'PEDIATRICS', '儿科', 1, 0, 0),
       (3, 'OBSTETRICS', '妇产科', 1, 0, 0),
       (4, 'GENERAL', '全科门诊', 1, 0, 0);

INSERT INTO clinic_room (id, dept_id, room_code, room_name, doctor_name, enabled, deleted, version)
VALUES (1, 1, 'EMG-01', '急诊1诊室', '张医生', 1, 0, 0),
       (2, 2, 'PED-01', '儿科1诊室', '李医生', 1, 0, 0),
       (3, 3, 'OBS-01', '妇产1诊室', '王医生', 1, 0, 0),
       (4, 4, 'GEN-01', '全科1诊室', '赵医生', 1, 0, 0);

INSERT INTO triage_rule (id, rule_code, rule_name, symptom_keyword, triage_level, recommend_dept_id, special_weight, fast_track, enabled, deleted, version)
VALUES (1, 'RULE_CHEST', '胸痛急诊优先', '胸痛', 1, 1, 60, 1, 1, 0, 0),
       (2, 'RULE_CHILD', '儿童儿科优先', '发热', 3, 2, 20, 0, 1, 0, 0),
       (3, 'RULE_PREGNANT', '孕产妇绿色通道', '孕', 2, 3, 80, 1, 1, 0, 0);

INSERT INTO sys_role (id, role_code, role_name, deleted, version)
VALUES (1, 'ADMIN', '管理员', 0, 0),
       (2, 'TRIAGE_NURSE', '分诊护士', 0, 0),
       (3, 'DOCTOR', '医生', 0, 0),
       (4, 'GUIDE_DESK', '导诊台', 0, 0);

INSERT INTO sys_permission (id, permission_code, permission_name, deleted, version)
VALUES (1, 'patient:manage', '患者档案管理', 0, 0),
       (2, 'visit:manage', '到诊登记管理', 0, 0),
       (3, 'triage:assess', '分诊评估', 0, 0),
       (4, 'triage:rule', '分诊规则维护', 0, 0),
       (5, 'queue:manage', '排队管理', 0, 0),
       (6, 'queue:call', '叫号操作', 0, 0),
       (7, 'dashboard:view', '看板查看', 0, 0);

INSERT INTO sys_user (id, username, password, nickname, role_code, dept_id, room_id, enabled, deleted, version)
VALUES (1, 'admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6MZL5d6M8rJpV0t1Ik5dUvNbK/CuG', '系统管理员', 'ADMIN', NULL, NULL, 1, 0, 0),
       (2, 'triage.nurse', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6MZL5d6M8rJpV0t1Ik5dUvNbK/CuG', '分诊护士', 'TRIAGE_NURSE', 1, NULL, 1, 0, 0),
       (3, 'doctor.zhang', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6MZL5d6M8rJpV0t1Ik5dUvNbK/CuG', '张医生', 'DOCTOR', 1, 1, 1, 0, 0),
       (4, 'guide.desk', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6MZL5d6M8rJpV0t1Ik5dUvNbK/CuG', '导诊台', 'GUIDE_DESK', 4, NULL, 1, 0, 0);
