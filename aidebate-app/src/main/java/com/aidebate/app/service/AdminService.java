package com.aidebate.app.service;

import com.aidebate.domain.model.AdminUser;
import com.aidebate.domain.model.DebateSession;
import com.aidebate.domain.model.SensitiveWord;
import com.aidebate.infrastructure.mapper.AdminUserMapper;
import com.aidebate.infrastructure.mapper.DebateSessionMapper;
import com.aidebate.infrastructure.mapper.SensitiveWordMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Admin Service
 * Handles admin authentication and management operations
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminUserMapper adminUserMapper;
    private final DebateSessionMapper debateSessionMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final ContentModerationService contentModerationService;
    
    // Simple in-memory session storage (in production, use Redis)
    private final Map<String, Long> adminSessions = new HashMap<>();

    /**
     * Admin authentication
     */
    @Transactional
    public Map<String, Object> authenticate(String username, String password) {
        log.info("Admin authentication attempt: {}", username);
        
        QueryWrapper<AdminUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        AdminUser admin = adminUserMapper.selectOne(wrapper);
        
        if (admin == null) {
            throw new RuntimeException("Admin user not found");
        }
        
        if (!admin.checkPassword(password)) {
            throw new RuntimeException("Invalid password");
        }
        
        // Update last login
        admin.updateLastLogin();
        adminUserMapper.updateById(admin);
        
        // Generate session token
        String token = UUID.randomUUID().toString();
        adminSessions.put(token, admin.getAdminId());
        
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("adminId", admin.getAdminId());
        result.put("username", admin.getUsername());
        result.put("message", "Login successful");
        
        return result;
    }

    /**
     * Logout admin
     */
    public void logout(String token) {
        adminSessions.remove(token);
    }

    /**
     * Validate admin token
     */
    public boolean validateToken(String token) {
        return adminSessions.containsKey(token);
    }

    /**
     * Get debate history with pagination
     */
    public Map<String, Object> getDebateHistory(Integer page, Integer size, String status, Long userId) {
        log.info("Fetching debate history: page={}, size={}, status={}, userId={}", page, size, status, userId);
        
        Page<DebateSession> pageable = new Page<>(page != null ? page : 1, size != null ? size : 20);
        QueryWrapper<DebateSession> wrapper = new QueryWrapper<>();
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        
        wrapper.orderByDesc("created_at");
        
        IPage<DebateSession> result = debateSessionMapper.selectPage(pageable, wrapper);
        
        Map<String, Object> response = new HashMap<>();
        response.put("debates", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("size", result.getSize());
        response.put("pages", result.getPages());
        
        return response;
    }

    /**
     * Get user statistics
     */
    public Map<String, Object> getUserStatistics(Long userId) {
        log.info("Calculating statistics for user: {}", userId);
        
        QueryWrapper<DebateSession> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("status", "COMPLETED");
        
        List<DebateSession> completedDebates = debateSessionMapper.selectList(wrapper);
        
        int totalDebates = completedDebates.size();
        // Updated: Changed from USER to AFFIRMATIVE for AI vs AI debates
        long wins = completedDebates.stream()
                .filter(d -> DebateSession.Winner.AFFIRMATIVE.equals(d.getWinner()))
                .count();
        
        double winRate = totalDebates > 0 ? (wins * 100.0 / totalDebates) : 0.0;
        
        // Updated: Changed from getFinalScoreUser to getFinalScoreAffirmative
        double avgAffirmativeScore = completedDebates.stream()
                .map(DebateSession::getFinalScoreAffirmative)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
        
        // Updated: Changed from getFinalScoreAi to getFinalScoreNegative
        double avgNegativeScore = completedDebates.stream()
                .map(DebateSession::getFinalScoreNegative)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("userId", userId);
        stats.put("totalDebates", totalDebates);
        stats.put("wins", wins);
        stats.put("winRate", String.format("%.2f%%", winRate));
        stats.put("avgAffirmativeScore", String.format("%.2f", avgAffirmativeScore));
        stats.put("avgNegativeScore", String.format("%.2f", avgNegativeScore));
        stats.put("recentDebates", completedDebates.stream()
                .limit(10)
                .map(d -> Map.of(
                        "sessionId", d.getSessionId(),
                        "topicId", d.getTopicId(),
                        "winner", d.getWinner(),
                        "completedAt", d.getCompletedAt()
                ))
                .toList());
        
        return stats;
    }

    /**
     * Export debates as CSV
     */
    public String exportDebates(String status, Long userId) {
        QueryWrapper<DebateSession> wrapper = new QueryWrapper<>();
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        
        List<DebateSession> debates = debateSessionMapper.selectList(wrapper);
        
        StringBuilder csv = new StringBuilder();
        // Updated CSV header for AI vs AI debates
        csv.append("Session ID,Topic ID,User ID,Status,Winner,Affirmative Score,Negative Score,Auto Play Speed,Started At,Completed At\n");
        
        for (DebateSession session : debates) {
            csv.append(session.getSessionId()).append(",")
               .append(session.getTopicId()).append(",")
               .append(session.getUserId()).append(",")
               .append(session.getStatus()).append(",")
               .append(session.getWinner() != null ? session.getWinner() : "").append(",")
               .append(session.getFinalScoreAffirmative() != null ? session.getFinalScoreAffirmative() : "").append(",")
               .append(session.getFinalScoreNegative() != null ? session.getFinalScoreNegative() : "").append(",")
               .append(session.getAutoPlaySpeed() != null ? session.getAutoPlaySpeed() : "").append(",")
               .append(session.getStartedAt() != null ? session.getStartedAt() : "").append(",")
               .append(session.getCompletedAt() != null ? session.getCompletedAt() : "").append("\n");
        }
        
        return csv.toString();
    }

    /**
     * Get sensitive words with pagination
     */
    public Map<String, Object> getSensitiveWords(Integer page, Integer size, String category) {
        Page<SensitiveWord> pageable = new Page<>(page != null ? page : 1, size != null ? size : 20);
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        
        wrapper.orderByDesc("created_at");
        
        IPage<SensitiveWord> result = sensitiveWordMapper.selectPage(pageable, wrapper);
        
        Map<String, Object> response = new HashMap<>();
        response.put("words", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("size", result.getSize());
        
        return response;
    }

    /**
     * Add sensitive word
     */
    @Transactional
    public SensitiveWord addSensitiveWord(String word, String category, String severity, Long adminId) {
        SensitiveWord sensitiveWord = SensitiveWord.builder()
                .word(word)
                .category(category)
                .severity(SensitiveWord.Severity.valueOf(severity))
                .isActive(true)
                .createdBy(adminId)
                .createdAt(LocalDateTime.now())
                .build();
        
        sensitiveWordMapper.insert(sensitiveWord);
        
        // Invalidate cache
        contentModerationService.invalidateCache();
        
        return sensitiveWord;
    }

    /**
     * Delete sensitive word
     */
    @Transactional
    public void deleteSensitiveWord(Long wordId) {
        sensitiveWordMapper.deleteById(wordId);
        contentModerationService.invalidateCache();
    }

    /**
     * Get system configuration
     */
    public Map<String, Object> getSystemConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxRounds", 5);
        config.put("argumentCharacterLimit", 500);
        config.put("turnTimeLimitSeconds", 180);
        config.put("moderationEnabled", true);
        return config;
    }
}
