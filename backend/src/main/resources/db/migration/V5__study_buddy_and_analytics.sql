-- Phase 5: AI Study Buddy conversations, messages, and analytics performance indexes

CREATE TABLE chat_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    classroom_id BIGINT NULL,
    material_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_conv_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_conv_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms(id) ON DELETE SET NULL,
    CONSTRAINT fk_chat_conv_material FOREIGN KEY (material_id) REFERENCES study_materials(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_chat_conv_student ON chat_conversations(student_id);
CREATE INDEX idx_chat_conv_classroom ON chat_conversations(classroom_id);
CREATE INDEX idx_chat_conv_material ON chat_conversations(material_id);

CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL, -- 'USER' or 'ASSISTANT'
    content MEDIUMTEXT NOT NULL,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    truncation_notice VARCHAR(255) NULL,
    provider_used VARCHAR(50) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_msg_conv FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_chat_msg_conv ON chat_messages(conversation_id);
CREATE INDEX idx_chat_msg_created ON chat_messages(created_at);

-- Additional indexes to optimize aggregate queries for analytics dashboards
CREATE INDEX idx_attempts_assessment_status ON assessment_attempts(assessment_id, status);
CREATE INDEX idx_attempts_student_status ON assessment_attempts(student_id, status);
