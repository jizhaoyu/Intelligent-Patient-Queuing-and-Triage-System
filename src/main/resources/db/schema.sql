CREATE TABLE IF NOT EXISTS patient_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_no VARCHAR(32) NOT NULL UNIQUE,
    patient_name VARCHAR(64) NOT NULL,
    gender VARCHAR(16),
    birth_date DATE,
    phone VARCHAR(32),
    id_card VARCHAR(32),
    allergy_history VARCHAR(255),
    special_tags VARCHAR(255),
    priority_revisit_pending TINYINT NOT NULL DEFAULT 0,
    priority_revisit_granted_time DATETIME,
    priority_revisit_granted_by VARCHAR(64),
    current_status VARCHAR(32),
    current_visit_id BIGINT,
    current_visit_no VARCHAR(32),
    current_dept_id BIGINT,
    current_room_id BIGINT,
    status_updated_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_patient_phone (phone)
);

CREATE TABLE IF NOT EXISTS visit_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    visit_no VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    register_time DATETIME,
    arrival_time DATETIME,
    chief_complaint VARCHAR(255),
    current_dept_id BIGINT,
    current_room_id BIGINT,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_visit_patient (patient_id),
    KEY idx_visit_status (status)
);

CREATE TABLE IF NOT EXISTS triage_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    rule_name VARCHAR(128) NOT NULL,
    symptom_keyword VARCHAR(64),
    triage_level INT,
    recommend_dept_id BIGINT,
    special_weight INT DEFAULT 0,
    fast_track TINYINT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS triage_assessment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    visit_id BIGINT NOT NULL,
    symptom_tags VARCHAR(255),
    body_temperature DECIMAL(5,2),
    heart_rate INT,
    blood_pressure VARCHAR(32),
    blood_oxygen INT,
    age INT,
    gender VARCHAR(16),
    elderly TINYINT DEFAULT 0,
    pregnant TINYINT DEFAULT 0,
    child TINYINT DEFAULT 0,
    disabled TINYINT DEFAULT 0,
    revisit TINYINT DEFAULT 0,
    triage_level INT NOT NULL,
    recommend_dept_id BIGINT,
    priority_score INT NOT NULL,
    fast_track TINYINT DEFAULT 0,
    manual_adjust_score INT DEFAULT 0,
    ai_suggested_level INT,
    ai_suggested_dept_id BIGINT,
    ai_priority_score INT,
    ai_risk_level VARCHAR(32),
    ai_risk_tags VARCHAR(255),
    ai_confidence DECIMAL(5,4),
    ai_advice VARCHAR(500),
    ai_need_manual_review TINYINT DEFAULT 0,
    ai_rule_diff VARCHAR(255),
    ai_model_version VARCHAR(64),
    ai_source VARCHAR(32),
    ai_audit_id BIGINT,
    assessor VARCHAR(64),
    assessed_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_assessment_visit (visit_id),
    KEY idx_assessment_ai_audit (ai_audit_id)
);

CREATE TABLE IF NOT EXISTS triage_ai_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    visit_id BIGINT,
    assessment_id BIGINT,
    scene VARCHAR(32) NOT NULL,
    chief_complaint VARCHAR(255),
    symptom_tags VARCHAR(255),
    ai_suggested_level INT,
    ai_suggested_dept_id BIGINT,
    ai_suggested_dept_name VARCHAR(64),
    ai_priority_score INT,
    ai_risk_level VARCHAR(32),
    ai_risk_tags VARCHAR(255),
    ai_confidence DECIMAL(5,4),
    ai_need_manual_review TINYINT DEFAULT 0,
    ai_advice VARCHAR(1000),
    ai_rule_diff VARCHAR(255),
    ai_source VARCHAR(32),
    ai_provider VARCHAR(32),
    ai_model_version VARCHAR(64),
    request_payload TEXT,
    response_payload TEXT,
    error_message VARCHAR(500),
    adopted TINYINT,
    final_triage_level INT,
    final_priority_score INT,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ai_audit_visit (visit_id),
    KEY idx_ai_audit_assessment (assessment_id)
);

CREATE TABLE IF NOT EXISTS clinic_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dept_code VARCHAR(32) NOT NULL UNIQUE,
    dept_name VARCHAR(64) NOT NULL,
    enabled TINYINT DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clinic_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dept_id BIGINT NOT NULL,
    room_code VARCHAR(32) NOT NULL UNIQUE,
    room_name VARCHAR(64) NOT NULL,
    doctor_name VARCHAR(64),
    enabled TINYINT DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_room_dept (dept_id)
);

CREATE TABLE IF NOT EXISTS queue_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no VARCHAR(32) NOT NULL UNIQUE,
    visit_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    assessment_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    room_id BIGINT,
    triage_level INT NOT NULL,
    priority_score INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    recall_count INT DEFAULT 0,
    fast_track TINYINT DEFAULT 0,
    source_type VARCHAR(32),
    source_remark VARCHAR(255),
    last_adjust_reason VARCHAR(255),
    consultation_locked TINYINT NOT NULL DEFAULT 0,
    consultation_locked_time DATETIME,
    room_assignment_status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    enqueue_time DATETIME,
    call_time DATETIME,
    complete_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_queue_status (status),
    KEY idx_queue_source (source_type),
    KEY idx_queue_dept_room (dept_id, room_id),
    KEY idx_queue_visit (visit_id)
);

CREATE TABLE IF NOT EXISTS queue_event_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    visit_id BIGINT,
    patient_id BIGINT,
    dept_id BIGINT,
    room_id BIGINT,
    operator_name VARCHAR(64),
    source_type VARCHAR(32),
    source_remark VARCHAR(255),
    remark VARCHAR(255),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_event_ticket (ticket_no)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    role_code VARCHAR(32) NOT NULL,
    dept_id BIGINT,
    room_id BIGINT,
    enabled TINYINT DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(64) NOT NULL UNIQUE,
    permission_name VARCHAR(64) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_role_user (user_id),
    KEY idx_user_role_role (role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_role_permission_role (role_id),
    KEY idx_role_permission_permission (permission_id)
);
