-- Migration: Per-Round Scoring System
-- Version: 3
-- Date: 2025-01-15
-- Description: Adds round_score_record table for per-round judge scoring

-- Create new round score record table
CREATE TABLE IF NOT EXISTS `round_score_record` (
    `round_score_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Unique round score identifier',
    `session_id` BIGINT NOT NULL COMMENT 'Reference to debate session',
    `round_number` INT NOT NULL COMMENT 'Round number (1-5)',
    `judge_role_id` BIGINT NOT NULL COMMENT 'Judge providing score',
    `debater_side` ENUM('AFFIRMATIVE', 'NEGATIVE') NOT NULL COMMENT 'Which side this score is for',
    `score` DECIMAL(5,2) NOT NULL COMMENT 'Score out of 100',
    `feedback` TEXT NULL COMMENT 'Judge reasoning for this score',
    `scored_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Scoring time',
    PRIMARY KEY (`round_score_id`),
    INDEX `idx_session_round` (`session_id`, `round_number`),
    INDEX `idx_judge` (`judge_role_id`),
    INDEX `idx_side` (`debater_side`),
    INDEX `idx_session_side` (`session_id`, `debater_side`, `round_number`),
    CONSTRAINT `fk_round_score_session` FOREIGN KEY (`session_id`) REFERENCES `debate_session` (`session_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_round_score_judge` FOREIGN KEY (`judge_role_id`) REFERENCES `role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Per-round scores from judges for each side';

-- Mark old scoring tables as deprecated (comments only, structure unchanged for backward compatibility)
ALTER TABLE `scoring_rule` COMMENT='[DEPRECATED] Legacy scoring criteria - replaced by per-round scoring';
ALTER TABLE `score_record` COMMENT='[DEPRECATED] Legacy argument-level scores - replaced by per-round scoring';
