package com.aidebate.app.service;

import com.aidebate.domain.model.*;
import com.aidebate.infrastructure.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Debate Session Service
 * Manages complete debate session lifecycle
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebateSessionService {

    private final DebateSessionMapper debateSessionMapper;
    private final RoleMapper roleMapper;
    private final ArgumentMapper argumentMapper;
    private final AlibabaAIService alibabaAIService;
    private final ContentModerationService contentModerationService;
    private final ScoringService scoringService;
    private final DebateTopicMapper topicMapper;
    private final ModeratorService moderatorService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize a new debate session with dual AI configuration
     */
    @Transactional
    public Map<String, Object> initializeSession(Long topicId, Long userId, Map<String, Map<String, String>> aiConfigs, String autoPlaySpeed) {
        log.info("Initializing AI vs AI debate session: topic={}, user={}", topicId, userId);

        // Create debate session
        DebateSession session = DebateSession.builder()
                .topicId(topicId)
                .userId(userId)
                .aiDebaterConfigs(convertToJson(aiConfigs))
                .autoPlaySpeed(DebateSession.AutoPlaySpeed.valueOf(autoPlaySpeed))
                .isPaused(false)
                .status(DebateSession.SessionStatus.INITIALIZED)
                .createdAt(LocalDateTime.now())
                .build();

        debateSessionMapper.insert(session);
        Long sessionId = session.getSessionId();

        // Create 7 roles (all AI-driven)
        createAIRoles(sessionId, aiConfigs);

        // Initialize scoring rules
        scoringService.createScoringRules(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "INITIALIZED");
        result.put("topicId", topicId);
        result.put("aiConfigs", aiConfigs);
        result.put("autoPlaySpeed", autoPlaySpeed);
        
        return result;
    }

    /**
     * Start a debate session
     */
    @Transactional
    public Map<String, Object> startSession(Long sessionId) {
        log.info("Starting debate session: {}", sessionId);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found: " + sessionId);
        }

        session.start();
        debateSessionMapper.updateById(session);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "IN_PROGRESS");
        result.put("startedAt", session.getStartedAt());
        result.put("currentRound", 1);
        result.put("maxRounds", 5);
        
        return result;
    }

    /**
     * Complete debate session with final scores
     */
    @Transactional
    public Map<String, Object> completeSession(Long sessionId) {
        log.info("Completing debate session: {}", sessionId);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found: " + sessionId);
        }

        // Get final scores
        Map<String, Object> scores = getCurrentScores(sessionId);
        BigDecimal affirmativeScore = (BigDecimal) scores.get("affirmativeScore");
        BigDecimal negativeScore = (BigDecimal) scores.get("negativeScore");

        // Determine winner
        DebateSession.Winner winner;
        if (affirmativeScore.compareTo(negativeScore) > 0) {
            winner = DebateSession.Winner.AFFIRMATIVE;
        } else if (negativeScore.compareTo(affirmativeScore) > 0) {
            winner = DebateSession.Winner.NEGATIVE;
        } else {
            winner = DebateSession.Winner.DRAW;
        }

        // Complete session
        session.complete(affirmativeScore, negativeScore, winner);
        debateSessionMapper.updateById(session);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "COMPLETED");
        result.put("winner", winner.name());
        result.put("affirmativeScore", affirmativeScore);
        result.put("negativeScore", negativeScore);
        result.put("completedAt", session.getCompletedAt());

        return result;
    }

    /**
     * Get current scores - updated for AI vs AI debates
     */
    public Map<String, Object> getCurrentScores(Long sessionId) {
        // Get all arguments for each side
        List<Argument> affirmativeArgs = getArgumentsForSide(sessionId, "AFFIRMATIVE");
        List<Argument> negativeArgs = getArgumentsForSide(sessionId, "NEGATIVE");
        
        // Calculate total scores
        BigDecimal affirmativeScore = affirmativeArgs.stream()
                .map(arg -> scoringService.calculateArgumentScore(arg.getArgumentId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal negativeScore = negativeArgs.stream()
                .map(arg -> scoringService.calculateArgumentScore(arg.getArgumentId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("affirmativeScore", affirmativeScore);
        result.put("negativeScore", negativeScore);
        result.put("sessionId", sessionId);

        return result;
    }
    
    private List<Argument> getArgumentsForSide(Long sessionId, String side) {
        Role role = getUserRole(sessionId, side);
        if (role == null) {
            return List.of();
        }
        
        QueryWrapper<Argument> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("role_id", role.getRoleId());
        wrapper.eq("is_preview", false);
        wrapper.orderByAsc("round_number");
        
        return argumentMapper.selectList(wrapper);
    }

    /**
     * Get session details
     */
    public Map<String, Object> getSessionDetails(Long sessionId) {
        DebateSession session = debateSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found: " + sessionId);
        }

        DebateTopic topic = topicMapper.selectById(session.getTopicId());
        List<Argument> arguments = getSessionArguments(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("session", session);
        result.put("topic", topic);
        result.put("arguments", arguments);
        result.put("scores", getCurrentScores(sessionId));

        return result;
    }

    /**
     * Get current session state - updated for AI vs AI
     */
    public Map<String, Object> getSessionState(Long sessionId) {
        DebateSession session = debateSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found: " + sessionId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", session.getStatus().name());
        result.put("currentRound", getCurrentRound(sessionId));
        result.put("maxRounds", 5);
        result.put("isPaused", session.isPaused());
        result.put("autoPlaySpeed", session.getAutoPlaySpeed());
        
        return result;
    }

    /**
     * Get current round number
     */
    private int getCurrentRound(Long sessionId) {
        List<Argument> arguments = getSessionArguments(sessionId);
        if (arguments.isEmpty()) {
            return 1;
        }
        return arguments.stream()
                .mapToInt(Argument::getRoundNumber)
                .max()
                .orElse(1);
    }

    // ========== Private Helper Methods ==========

    private void createAIRoles(Long sessionId, Map<String, Map<String, String>> aiConfigs) {
        String affirmativeConfigJson = convertToJson(aiConfigs.get("affirmative"));
        String negativeConfigJson = convertToJson(aiConfigs.get("negative"));
        String defaultConfigJson = convertToJson(Map.of("personality", "Professional", "expertiseLevel", "Expert"));

        // All AI-driven roles
        createRole(sessionId, Role.RoleType.ORGANIZER, true, null, defaultConfigJson);
        createRole(sessionId, Role.RoleType.MODERATOR, true, null, defaultConfigJson);
        createRole(sessionId, Role.RoleType.JUDGE_1, true, null, defaultConfigJson);
        createRole(sessionId, Role.RoleType.JUDGE_2, true, null, defaultConfigJson);
        createRole(sessionId, Role.RoleType.JUDGE_3, true, null, defaultConfigJson);
        createRole(sessionId, Role.RoleType.AFFIRMATIVE, true, null, affirmativeConfigJson);
        createRole(sessionId, Role.RoleType.NEGATIVE, true, null, negativeConfigJson);
    }

    private void createRoles(Long sessionId, Long userId, String userSide, Map<String, String> aiConfig) {
        // Legacy method - kept for backward compatibility but not used in new AI vs AI flow
        String aiConfigJson = convertToJson(aiConfig);
        createRole(sessionId, Role.RoleType.ORGANIZER, true, null, aiConfigJson);
        createRole(sessionId, Role.RoleType.MODERATOR, true, null, aiConfigJson);
        createRole(sessionId, Role.RoleType.JUDGE_1, true, null, aiConfigJson);
        createRole(sessionId, Role.RoleType.JUDGE_2, true, null, aiConfigJson);
        createRole(sessionId, Role.RoleType.JUDGE_3, true, null, aiConfigJson);

        // Debater roles  - both are now AI in new system
        createRole(sessionId, Role.RoleType.AFFIRMATIVE, true, null, aiConfigJson);
        createRole(sessionId, Role.RoleType.NEGATIVE, true, null, aiConfigJson);
    }

    private void createRole(Long sessionId, Role.RoleType roleType, boolean isAi, Long userId, String aiConfig) {
        Role role = Role.builder()
                .sessionId(sessionId)
                .roleType(roleType)
                .isAi(isAi)
                .assignedUserId(userId)
                .aiConfig(aiConfig)
                .createdAt(LocalDateTime.now())
                .build();
        roleMapper.insert(role);
    }

    private Role getUserRole(Long sessionId, String side) {
        Role.RoleType roleType = side.equals("AFFIRMATIVE") ? Role.RoleType.AFFIRMATIVE : Role.RoleType.NEGATIVE;
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("role_type", roleType);
        return roleMapper.selectOne(wrapper);
    }

    // Legacy method - no longer used
    // Deprecated - use DebateOrchestrationService.generateRound() instead
    private String generateAIResponse(Long sessionId, int roundNumber, String side) {
        throw new UnsupportedOperationException("This method is deprecated - use DebateOrchestrationService instead");
    }

    // Legacy method - no longer used as it references removed getAiOpponentConfig()
    // Kept for reference only - should not be called
    private String generateAIResponseWithContext_DEPRECATED(Long sessionId, int roundNumber, String side, String moderatorInstruction) {
        throw new UnsupportedOperationException("This method is deprecated - use DebateOrchestrationService instead");
    }

    /**
     * Get role type name for an argument
     */
    private String getRoleType(Long roleId, Long sessionId) {
        Role role = roleMapper.selectById(roleId);
        if (role != null && role.getRoleType() != null) {
            return role.getRoleType().name();
        }
        return "UNKNOWN";
    }

    // Legacy helper methods - no longer used in AI vs AI system
    // These methods referenced removed getUserSide() and are kept only for reference
    // They should not be called in the new automated debate flow
    
    private List<Argument> getSessionArguments(Long sessionId) {
        QueryWrapper<Argument> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("is_preview", false);
        wrapper.orderByAsc("round_number");
        wrapper.orderByAsc("submitted_at");

        return argumentMapper.selectList(wrapper);
    }

    private Map<String, String> parseAiConfig(String aiConfigJson) {
        try {
            return objectMapper.readValue(aiConfigJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse AI config, using defaults", e);
            Map<String, String> defaults = new HashMap<>();
            defaults.put("personality", "Analytical");
            defaults.put("expertiseLevel", "Expert");
            return defaults;
        }
    }

    private String convertToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert object to JSON", e);
            return "{}";
        }
    }
}
