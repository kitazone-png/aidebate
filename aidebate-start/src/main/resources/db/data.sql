-- =============================================
-- AI Debate Simulator Initial Data
-- Database: aidebate
-- =============================================

USE aidebate;

-- =============================================
-- Insert Default Admin User
-- Username: admin
-- Password: admin (BCrypt hashed)
-- =============================================
INSERT INTO `admin_user` (`username`, `password_hash`, `created_at`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8MFDN6B5FpGqSGvDLu', NOW())
ON DUPLICATE KEY UPDATE `username` = 'admin';

-- =============================================
-- Insert System Configuration
-- =============================================
INSERT INTO `system_configuration` (`config_key`, `config_value`, `description`, `updated_at`)
VALUES 
    ('SENSITIVE_WORD_VALIDATION_ENABLED', 'true', 'Enable/disable sensitive word validation', NOW()),
    ('MAX_DEBATE_ROUNDS', '5', 'Maximum number of rounds per debate', NOW()),
    ('ARGUMENT_CHARACTER_LIMIT', '500', 'Maximum characters per argument', NOW()),
    ('TURN_TIME_LIMIT_SECONDS', '180', 'Time limit per turn in seconds', NOW())
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- =============================================
-- Insert Sample Sensitive Words
-- =============================================
INSERT INTO `sensitive_word` (`word`, `category`, `severity`, `is_active`, `created_at`)
VALUES 
    ('badword', 'profanity', 'MEDIUM', TRUE, NOW()),
    ('offensive', 'inappropriate', 'LOW', TRUE, NOW()),
    ('violence', 'violence', 'HIGH', TRUE, NOW())
ON DUPLICATE KEY UPDATE `word` = VALUES(`word`);

-- =============================================
-- Insert Sample Debate Topics
-- =============================================
INSERT INTO `debate_topic` (`title`, `description`, `source`, `category`, `is_active`, `created_at`)
VALUES 
    ('Artificial Intelligence and Employment', 
     'Should governments regulate AI to protect jobs, or let market forces determine employment shifts?', 
     'HOT_TOPIC', 'Technology', TRUE, NOW()),
    ('Climate Change Policy', 
     'Should individual countries prioritize economic growth over environmental regulations?', 
     'HOT_TOPIC', 'Environment', TRUE, NOW()),
    ('Education Reform', 
     'Is traditional classroom learning more effective than online education?', 
     'HOT_TOPIC', 'Education', TRUE, NOW()),
    ('Healthcare System', 
     'Should healthcare be fully funded by the government or remain privatized?', 
     'HOT_TOPIC', 'Healthcare', TRUE, NOW()),
    ('Privacy vs Security', 
     'Should governments have access to encrypted communications for national security?', 
     'HOT_TOPIC', 'Technology', TRUE, NOW())
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- =============================================
-- Insert Sample User (for testing)
-- Username: testuser
-- Password: password123 (BCrypt hashed)
-- =============================================
INSERT INTO `user` (`username`, `email`, `password_hash`, `is_active`, `created_at`)
VALUES ('testuser', 'testuser@example.com', '$2a$10$e0MYzXyjpJS7Pd0RVvHwHe1C5gJt3IiqJ01hc/Xe5WtjQsE0LzT5O', TRUE, NOW())
ON DUPLICATE KEY UPDATE `username` = 'testuser';
