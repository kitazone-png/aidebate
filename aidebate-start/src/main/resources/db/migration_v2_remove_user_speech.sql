-- =============================================
-- Migration: Remove User Speech Functionality
-- Version: 2.0
-- Description: Transform to fully AI-driven debate system
-- =============================================

USE aidebate;

-- Step 1: Add new fields to debate_session table
ALTER TABLE `debate_session`
ADD COLUMN `auto_play_speed` ENUM('FAST', 'NORMAL', 'SLOW') NULL DEFAULT 'NORMAL' COMMENT 'Delay between AI argument generations' AFTER `ai_opponent_config`,
ADD COLUMN `is_paused` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Current pause state' AFTER `auto_play_speed`,
ADD COLUMN `current_position` VARCHAR(100) NULL COMMENT 'Current execution position for pause/resume' AFTER `is_paused`;

-- Step 2: Modify user_id to be nullable (allow anonymous sessions)
ALTER TABLE `debate_session`
MODIFY COLUMN `user_id` BIGINT NULL COMMENT 'Participating user (optional for AI-only debates)';

-- Step 3: Drop foreign key constraint on user_id to allow NULL
ALTER TABLE `debate_session`
DROP FOREIGN KEY `fk_session_user`;

-- Step 4: Add back foreign key constraint allowing NULL
ALTER TABLE `debate_session`
ADD CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE SET NULL;

-- Step 5: Rename ai_opponent_config column to ai_debater_configs
ALTER TABLE `debate_session`
CHANGE COLUMN `ai_opponent_config` `ai_debater_configs` JSON NOT NULL COMMENT 'AI configurations for both affirmative and negative debaters';

-- Step 6: Drop user_side column (no longer needed)
ALTER TABLE `debate_session`
DROP COLUMN `user_side`;

-- Step 7: Update winner enum to use debate sides instead of user/AI
ALTER TABLE `debate_session`
MODIFY COLUMN `winner` ENUM('AFFIRMATIVE', 'NEGATIVE', 'DRAW') NULL COMMENT 'Debate winner by side';

-- Step 8: Rename score columns to reflect debate sides
ALTER TABLE `debate_session`
CHANGE COLUMN `final_score_user` `final_score_affirmative` DECIMAL(5,2) NULL COMMENT 'Affirmative side final score',
CHANGE COLUMN `final_score_ai` `final_score_negative` DECIMAL(5,2) NULL COMMENT 'Negative side final score';

-- Step 9: Add PAUSED status to session status enum
ALTER TABLE `debate_session`
MODIFY COLUMN `status` ENUM('INITIALIZED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'ABORTED') NOT NULL DEFAULT 'INITIALIZED' COMMENT 'Session status';

-- Step 10: Update existing data (set default values for new fields)
UPDATE `debate_session`
SET `auto_play_speed` = 'NORMAL',
    `is_paused` = FALSE
WHERE `auto_play_speed` IS NULL;

-- =============================================
-- Migration Complete
-- =============================================
-- Note: Existing sessions will have NULL user_side (removed column)
-- New sessions will use dual AI configuration structure
-- =============================================
