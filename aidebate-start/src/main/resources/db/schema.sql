-- =============================================
-- AI Debate Simulator Database Schema
-- Database: aidebate
-- Character Set: utf8mb4
-- Collation: utf8mb4_unicode_ci
-- =============================================

-- Create Database
CREATE DATABASE IF NOT EXISTS aidebate DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aidebate;

-- =============================================
-- Table: user
-- Description: User account information
-- =============================================
CREATE TABLE IF NOT EXISTS `user` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique user identifier',
    `username` VARCHAR(50) NOT NULL COMMENT 'User login name',
    `email` VARCHAR(100) NOT NULL COMMENT 'User email address',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'Hashed password',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation time',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Account active status',
    PRIMARY KEY (`user_id`),
    UNIQUE INDEX `idx_username` (`username`),
    UNIQUE INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User account information';

-- =============================================
-- Table: admin_user
-- Description: Admin account information
-- =============================================
CREATE TABLE IF NOT EXISTS `admin_user` (
    `admin_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique admin identifier',
    `username` VARCHAR(50) NOT NULL COMMENT 'Login username',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'Hashed password',
    `last_login_at` TIMESTAMP NULL COMMENT 'Last login time',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation time',
    PRIMARY KEY (`admin_id`),
    UNIQUE INDEX `idx_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Admin account information';

-- =============================================
-- Table: debate_topic
-- Description: Debate topics (hot, custom, AI-generated)
-- =============================================
CREATE TABLE IF NOT EXISTS `debate_topic` (
    `topic_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique topic identifier',
    `title` VARCHAR(200) NOT NULL COMMENT 'Topic title',
    `description` TEXT NULL COMMENT 'Detailed description',
    `source` ENUM('HOT_TOPIC', 'USER_CUSTOM', 'AI_GENERATED') NOT NULL COMMENT 'Topic source type',
    `category` VARCHAR(50) NULL COMMENT 'Topic category',
    `created_by` BIGINT NULL COMMENT 'User who created topic',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Active status',
    PRIMARY KEY (`topic_id`),
    INDEX `idx_source` (`source`),
    INDEX `idx_category` (`category`),
    INDEX `idx_created_at` (`created_at`),
    CONSTRAINT `fk_topic_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Debate topics';

-- =============================================
-- Table: debate_session
-- Description: Debate sessions and results
-- =============================================
CREATE TABLE IF NOT EXISTS `debate_session` (
    `session_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique session identifier',
    `topic_id` BIGINT NOT NULL COMMENT 'Reference to debate topic',
    `user_id` BIGINT NOT NULL COMMENT 'Participating user',
    `user_side` ENUM('AFFIRMATIVE', 'NEGATIVE') NOT NULL COMMENT 'User''s debate side',
    `ai_opponent_config` JSON NOT NULL COMMENT 'AI personality and level settings',
    `status` ENUM('INITIALIZED', 'IN_PROGRESS', 'COMPLETED', 'ABORTED') NOT NULL DEFAULT 'INITIALIZED' COMMENT 'Session status',
    `started_at` TIMESTAMP NULL COMMENT 'Session start time',
    `completed_at` TIMESTAMP NULL COMMENT 'Session completion time',
    `final_score_user` DECIMAL(5,2) NULL COMMENT 'User''s final score',
    `final_score_ai` DECIMAL(5,2) NULL COMMENT 'AI''s final score',
    `winner` ENUM('USER', 'AI', 'DRAW') NULL COMMENT 'Debate winner',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    PRIMARY KEY (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_topic_id` (`topic_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`),
    CONSTRAINT `fk_session_topic` FOREIGN KEY (`topic_id`) REFERENCES `debate_topic` (`topic_id`),
    CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Debate sessions and results';

-- =============================================
-- Table: role
-- Description: Roles in debates (organizer, moderator, judges, debaters)
-- =============================================
CREATE TABLE IF NOT EXISTS `role` (
    `role_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique role identifier',
    `session_id` BIGINT NOT NULL COMMENT 'Reference to debate session',
    `role_type` ENUM('ORGANIZER', 'MODERATOR', 'JUDGE_1', 'JUDGE_2', 'JUDGE_3', 'AFFIRMATIVE', 'NEGATIVE') NOT NULL COMMENT 'Role type',
    `is_ai` BOOLEAN NOT NULL COMMENT 'Whether role is AI-driven',
    `assigned_user_id` BIGINT NULL COMMENT 'User assigned to role',
    `ai_config` JSON NULL COMMENT 'AI configuration if applicable',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    PRIMARY KEY (`role_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_role_type` (`role_type`),
    CONSTRAINT `fk_role_session` FOREIGN KEY (`session_id`) REFERENCES `debate_session` (`session_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_role_user` FOREIGN KEY (`assigned_user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Roles in debates';

-- =============================================
-- Table: argument
-- Description: Arguments submitted during debates
-- =============================================
CREATE TABLE IF NOT EXISTS `argument` (
    `argument_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique argument identifier',
    `session_id` BIGINT NOT NULL COMMENT 'Reference to debate session',
    `role_id` BIGINT NOT NULL COMMENT 'Role making the argument',
    `round_number` INT NOT NULL COMMENT 'Debate round number',
    `argument_text` TEXT NOT NULL COMMENT 'The actual argument content',
    `is_preview` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether this is preview content',
    `submitted_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Submission time',
    `character_count` INT NOT NULL COMMENT 'Number of characters',
    `validation_status` ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT 'Content validation status',
    `validation_message` VARCHAR(500) NULL COMMENT 'Validation result message',
    PRIMARY KEY (`argument_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_round_number` (`round_number`),
    INDEX `idx_submitted_at` (`submitted_at`),
    CONSTRAINT `fk_argument_session` FOREIGN KEY (`session_id`) REFERENCES `debate_session` (`session_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_argument_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Arguments submitted during debates';

-- =============================================
-- Table: scoring_rule
-- Description: Scoring criteria definitions
-- =============================================
CREATE TABLE IF NOT EXISTS `scoring_rule` (
    `rule_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique rule identifier',
    `session_id` BIGINT NOT NULL COMMENT 'Reference to debate session',
    `criteria_name` VARCHAR(100) NOT NULL COMMENT 'Scoring criterion name',
    `max_score` INT NOT NULL COMMENT 'Maximum possible score',
    `weight` DECIMAL(3,2) NOT NULL COMMENT 'Weight in final calculation (0-1)',
    `description` TEXT NULL COMMENT 'Criterion description',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    PRIMARY KEY (`rule_id`),
    INDEX `idx_session_id` (`session_id`),
    CONSTRAINT `fk_rule_session` FOREIGN KEY (`session_id`) REFERENCES `debate_session` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Scoring criteria definitions';

-- =============================================
-- Table: score_record
-- Description: Individual scores from judges
-- =============================================
CREATE TABLE IF NOT EXISTS `score_record` (
    `score_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique score identifier',
    `session_id` BIGINT NOT NULL COMMENT 'Reference to debate session',
    `argument_id` BIGINT NOT NULL COMMENT 'Reference to argument being scored',
    `judge_role_id` BIGINT NOT NULL COMMENT 'Judge providing score',
    `rule_id` BIGINT NOT NULL COMMENT 'Scoring rule applied',
    `score` DECIMAL(5,2) NOT NULL COMMENT 'Actual score given',
    `feedback` TEXT NULL COMMENT 'Judge''s feedback comments',
    `scored_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Scoring time',
    PRIMARY KEY (`score_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_argument_id` (`argument_id`),
    INDEX `idx_judge_role_id` (`judge_role_id`),
    CONSTRAINT `fk_score_session` FOREIGN KEY (`session_id`) REFERENCES `debate_session` (`session_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_score_argument` FOREIGN KEY (`argument_id`) REFERENCES `argument` (`argument_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_score_judge` FOREIGN KEY (`judge_role_id`) REFERENCES `role` (`role_id`),
    CONSTRAINT `fk_score_rule` FOREIGN KEY (`rule_id`) REFERENCES `scoring_rule` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Individual scores from judges';

-- =============================================
-- Table: feedback
-- Description: Performance feedback for users
-- =============================================
CREATE TABLE IF NOT EXISTS `feedback` (
    `feedback_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique feedback identifier',
    `session_id` BIGINT NOT NULL COMMENT 'Reference to debate session',
    `user_id` BIGINT NOT NULL COMMENT 'User receiving feedback',
    `logic_score` DECIMAL(5,2) NOT NULL COMMENT 'Logic quality score',
    `persuasiveness_score` DECIMAL(5,2) NOT NULL COMMENT 'Persuasiveness score',
    `fluency_score` DECIMAL(5,2) NOT NULL COMMENT 'Fluency score',
    `overall_assessment` TEXT NOT NULL COMMENT 'General assessment',
    `improvements` JSON NOT NULL COMMENT 'Array of improvement suggestions',
    `generated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Feedback generation time',
    PRIMARY KEY (`feedback_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    CONSTRAINT `fk_feedback_session` FOREIGN KEY (`session_id`) REFERENCES `debate_session` (`session_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Performance feedback for users';

-- =============================================
-- Table: sensitive_word
-- Description: Sensitive word dictionary
-- =============================================
CREATE TABLE IF NOT EXISTS `sensitive_word` (
    `word_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique word identifier',
    `word` VARCHAR(100) NOT NULL COMMENT 'Sensitive word or phrase',
    `category` VARCHAR(50) NULL COMMENT 'Word category',
    `severity` ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL COMMENT 'Severity level',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether word is currently active',
    `created_by` BIGINT NULL COMMENT 'Admin who added the word',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (`word_id`),
    UNIQUE INDEX `idx_word` (`word`),
    INDEX `idx_category` (`category`),
    INDEX `idx_severity` (`severity`),
    INDEX `idx_is_active` (`is_active`),
    CONSTRAINT `fk_word_creator` FOREIGN KEY (`created_by`) REFERENCES `admin_user` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sensitive word dictionary';

-- =============================================
-- Table: system_configuration
-- Description: System configuration settings
-- =============================================
CREATE TABLE IF NOT EXISTS `system_configuration` (
    `config_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique configuration identifier',
    `config_key` VARCHAR(100) NOT NULL COMMENT 'Configuration key',
    `config_value` VARCHAR(500) NOT NULL COMMENT 'Configuration value',
    `description` TEXT NULL COMMENT 'Configuration description',
    `updated_by` BIGINT NULL COMMENT 'Admin who updated',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (`config_id`),
    UNIQUE INDEX `idx_config_key` (`config_key`),
    CONSTRAINT `fk_config_updater` FOREIGN KEY (`updated_by`) REFERENCES `admin_user` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System configuration settings';
