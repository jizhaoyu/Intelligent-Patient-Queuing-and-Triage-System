package com.hospital.triage.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "deleted", Integer.class, 0);
                strictInsertFill(metaObject, "version", Integer.class, 0);
                strictInsertFill(metaObject, "createdTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }

    @Bean
    public CommandLineRunner queueSchemaCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            ensureColumnExists(jdbcTemplate, "queue_ticket", "source_type", "ALTER TABLE queue_ticket ADD COLUMN source_type VARCHAR(32) NULL AFTER fast_track");
            ensureColumnExists(jdbcTemplate, "queue_ticket", "source_remark", "ALTER TABLE queue_ticket ADD COLUMN source_remark VARCHAR(255) NULL AFTER source_type");
            ensureColumnExists(jdbcTemplate, "queue_ticket", "last_adjust_reason", "ALTER TABLE queue_ticket ADD COLUMN last_adjust_reason VARCHAR(255) NULL AFTER source_remark");
            ensureColumnExists(jdbcTemplate, "queue_ticket", "consultation_locked",
                    "ALTER TABLE queue_ticket ADD COLUMN consultation_locked TINYINT NOT NULL DEFAULT 0 AFTER last_adjust_reason");
            ensureColumnExists(jdbcTemplate, "queue_ticket", "consultation_locked_time",
                    "ALTER TABLE queue_ticket ADD COLUMN consultation_locked_time DATETIME NULL AFTER consultation_locked");
            ensureColumnExists(jdbcTemplate, "queue_ticket", "room_assignment_status",
                    "ALTER TABLE queue_ticket ADD COLUMN room_assignment_status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED' AFTER consultation_locked_time");
            ensureColumnExists(jdbcTemplate, "queue_event_log", "source_type", "ALTER TABLE queue_event_log ADD COLUMN source_type VARCHAR(32) NULL AFTER operator_name");
            ensureColumnExists(jdbcTemplate, "queue_event_log", "source_remark", "ALTER TABLE queue_event_log ADD COLUMN source_remark VARCHAR(255) NULL AFTER source_type");
            ensureColumnExists(jdbcTemplate, "patient_info", "priority_revisit_pending",
                    "ALTER TABLE patient_info ADD COLUMN priority_revisit_pending TINYINT NOT NULL DEFAULT 0 AFTER special_tags");
            ensureColumnExists(jdbcTemplate, "patient_info", "priority_revisit_granted_time",
                    "ALTER TABLE patient_info ADD COLUMN priority_revisit_granted_time DATETIME NULL AFTER priority_revisit_pending");
            ensureColumnExists(jdbcTemplate, "patient_info", "priority_revisit_granted_by",
                    "ALTER TABLE patient_info ADD COLUMN priority_revisit_granted_by VARCHAR(64) NULL AFTER priority_revisit_granted_time");

            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_suggested_level",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_suggested_level INT NULL AFTER manual_adjust_score");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_suggested_dept_id",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_suggested_dept_id BIGINT NULL AFTER ai_suggested_level");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_priority_score",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_priority_score INT NULL AFTER ai_suggested_dept_id");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_risk_level",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_risk_level VARCHAR(32) NULL AFTER ai_priority_score");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_risk_tags",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_risk_tags VARCHAR(255) NULL AFTER ai_risk_level");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_confidence",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_confidence DECIMAL(5,4) NULL AFTER ai_risk_tags");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_advice",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_advice VARCHAR(500) NULL AFTER ai_confidence");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_need_manual_review",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_need_manual_review TINYINT DEFAULT 0 AFTER ai_advice");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_rule_diff",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_rule_diff VARCHAR(255) NULL AFTER ai_need_manual_review");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_model_version",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_model_version VARCHAR(64) NULL AFTER ai_rule_diff");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_source",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_source VARCHAR(32) NULL AFTER ai_model_version");
            ensureColumnExists(jdbcTemplate, "triage_assessment", "ai_audit_id",
                    "ALTER TABLE triage_assessment ADD COLUMN ai_audit_id BIGINT NULL AFTER ai_source");

            ensureTableExists(jdbcTemplate, "triage_ai_audit", """
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
                    )
                    """);
        };
    }

    private void ensureTableExists(JdbcTemplate jdbcTemplate, String tableName, String ddl) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void ensureColumnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName, String ddl) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.execute(ddl);
        }
    }
}
