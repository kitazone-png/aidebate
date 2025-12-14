package com.aidebate.app.service;

import com.aidebate.domain.model.Argument;
import com.aidebate.domain.model.DebateSession;
import com.aidebate.domain.model.DebateTopic;
import com.aidebate.domain.model.Role;
import com.aidebate.infrastructure.mapper.ArgumentMapper;
import com.aidebate.infrastructure.mapper.DebateSessionMapper;
import com.aidebate.infrastructure.mapper.DebateTopicMapper;
import com.aidebate.infrastructure.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debate Orchestration Service
 * Coordinates automated AI vs AI debate flow with streaming
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
public class DebateOrchestrationService {

    private final DebateSessionMapper debateSessionMapper;
    private final DebateTopicMapper topicMapper;
    private final RoleMapper roleMapper;
    private final ArgumentMapper argumentMapper;
    private final AlibabaAIService alibabaAIService;
    private final ModeratorService moderatorService;
    private final ScoringService scoringService;

    public DebateOrchestrationService(
            DebateSessionMapper debateSessionMapper,
            DebateTopicMapper topicMapper,
            RoleMapper roleMapper,
            ArgumentMapper argumentMapper,
            AlibabaAIService alibabaAIService,
            ModeratorService moderatorService,
            ScoringService scoringService) {
        this.debateSessionMapper = debateSessionMapper;
        this.topicMapper = topicMapper;
        this.roleMapper = roleMapper;
        this.argumentMapper = argumentMapper;
        this.alibabaAIService = alibabaAIService;
        this.moderatorService = moderatorService;
        this.scoringService = scoringService;
    }

    /**
     * Stream automated debate flow
     */
    public void streamAutomatedDebate(Long sessionId, String language, SseEmitter emitter) {
        log.info("Starting automated debate streaming for session: {}", sessionId);

        try {
            DebateSession session = debateSessionMapper.selectById(sessionId);
            if (session == null) {
                sendError(emitter, "Session not found");
                return;
            }

            // Check if paused
            if (session.isPaused()) {
                log.info("Session {} is paused, resuming from position: {}", sessionId, session.getCurrentPosition());
                resumeFromPosition(sessionId, language, emitter, session.getCurrentPosition());
                return;
            }

            // Get topic
            DebateTopic topic = topicMapper.selectById(session.getTopicId());

            // Get delay based on auto-play speed
            long delayMs = getDelayForSpeed(session.getAutoPlaySpeed());

            // Opening sequence
            generateOpeningSequence(sessionId, topic, language, emitter);
            Thread.sleep(delayMs);

            // 5 rounds of debate
            for (int round = 1; round <= 5; round++) {
                if (checkPaused(sessionId)) {
                    session.pause("round_" + round);
                    debateSessionMapper.updateById(session);
                    sendEvent(emitter, "debate_paused", Map.of("round", round, "position", "round_" + round));
                    return;
                }

                generateRound(sessionId, round, language, emitter, delayMs);
            }

            // Final judging
            generateJudgingSequence(sessionId, language, emitter);

            // Complete
            sendEvent(emitter, "debate_complete", Map.of("sessionId", sessionId, "timestamp", LocalDateTime.now().toString()));
            emitter.complete();

        } catch (Exception e) {
            log.error("Error in automated debate streaming", e);
            sendError(emitter, "Debate streaming failed: " + e.getMessage());
        }
    }

    /**
     * Generate opening sequence
     */
    private void generateOpeningSequence(Long sessionId, DebateTopic topic, String language, SseEmitter emitter) {
        log.info("Generating opening sequence for session: {}", sessionId);

        // Debate start event
        sendEvent(emitter, "debate_start", Map.of(
                "sessionId", sessionId,
                "topic", topic.getTitle(),
                "timestamp", LocalDateTime.now().toString()
        ));

        // Organizer rules announcement (streamed)
        moderatorService.generateOrganizerRulesStream(sessionId, language, (chunk, isComplete) -> {
            sendEvent(emitter, "organizer_rules", Map.of(
                    "chunk", chunk,
                    "complete", isComplete,
                    "timestamp", LocalDateTime.now().toString()
            ));
        });

        // Moderator introduction (streamed)
        moderatorService.generateDebateIntroductionStream(sessionId, topic.getTitle(), language, (chunk, isComplete) -> {
            sendEvent(emitter, "moderator_introduction", Map.of(
                    "chunk", chunk,
                    "complete", isComplete,
                    "timestamp", LocalDateTime.now().toString()
            ));
        });
    }

    /**
     * Generate single round
     */
    private void generateRound(Long sessionId, int roundNumber, String language, SseEmitter emitter, long delayMs) throws InterruptedException {
        log.info("Generating round {} for session: {}", roundNumber, sessionId);

        // Round start
        sendEvent(emitter, "round_start", Map.of("round", roundNumber, "timestamp", LocalDateTime.now().toString()));

        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());

        // Get AI configurations
        Map<String, Map<String, String>> aiConfigs = parseAiConfigs(session.getAiDebaterConfigs());

        // Get roles
        Role affirmativeRole = getRole(sessionId, "AFFIRMATIVE");
        Role negativeRole = getRole(sessionId, "NEGATIVE");

        // Get argument history
        List<Argument> history = getSessionArguments(sessionId);

        // Check pause before affirmative argument
        if (checkPaused(sessionId)) {
            session.pause(String.format("round_%d_affirmative_before", roundNumber));
            debateSessionMapper.updateById(session);
            sendEvent(emitter, "debate_paused", Map.of("round", roundNumber, "position", "affirmative_before", "speaker", "AFFIRMATIVE"));
            return;
        }

        // ===== AFFIRMATIVE ARGUMENT =====
        StringBuilder affirmativeArg = new StringBuilder();
        
        alibabaAIService.generateDebateArgumentStream(
                sessionId,
                roundNumber,
                topic.getTitle(),
                "AFFIRMATIVE",
                history,
                aiConfigs.get("affirmative"),
                "",
                (chunk, isComplete) -> {
                    affirmativeArg.append(chunk);
                    sendEvent(emitter, "ai_argument", Map.of(
                            "side", "AFFIRMATIVE",
                            "chunk", isComplete ? affirmativeArg.toString() : chunk,
                            "complete", isComplete,
                            "round", roundNumber,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                }
        );

        // Store affirmative argument
        Argument affirmativeArgument = Argument.builder()
                .sessionId(sessionId)
                .roleId(affirmativeRole.getRoleId())
                .roundNumber(roundNumber)
                .argumentText(affirmativeArg.toString())
                .characterCount(affirmativeArg.length())
                .validationStatus(Argument.ValidationStatus.APPROVED)
                .isPreview(false)
                .submittedAt(LocalDateTime.now())
                .build();
        argumentMapper.insert(affirmativeArgument);

        Thread.sleep(delayMs / 2);

        // Check pause before affirmative moderator feedback
        if (checkPaused(sessionId)) {
            session.pause(String.format("round_%d_affirmative_after", roundNumber));
            debateSessionMapper.updateById(session);
            sendEvent(emitter, "debate_paused", Map.of("round", roundNumber, "position", "affirmative_after", "speaker", "MODERATOR"));
            return;
        }

        // Moderator summary for affirmative
        moderatorService.generateArgumentSummaryStream(
                affirmativeArgument.getArgumentId(),
                sessionId,
                language,
                (chunk, isComplete) -> {
                    sendEvent(emitter, "moderator_summary", Map.of(
                            "side", "AFFIRMATIVE",
                            "chunk", chunk,
                            "complete", isComplete,
                            "round", roundNumber,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                }
        );

        // Moderator evaluation for affirmative
        moderatorService.generateArgumentEvaluationStream(
                affirmativeArgument.getArgumentId(),
                sessionId,
                language,
                (chunk, isComplete) -> {
                    sendEvent(emitter, "moderator_evaluation", Map.of(
                            "side", "AFFIRMATIVE",
                            "chunk", chunk,
                            "complete", isComplete,
                            "round", roundNumber,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                }
        );

        Thread.sleep(delayMs);

        // Check pause before negative argument
        if (checkPaused(sessionId)) {
            session.pause(String.format("round_%d_negative_before", roundNumber));
            debateSessionMapper.updateById(session);
            sendEvent(emitter, "debate_paused", Map.of("round", roundNumber, "position", "negative_before", "speaker", "NEGATIVE"));
            return;
        }

        // ===== NEGATIVE ARGUMENT =====
        // Refresh history with affirmative argument
        history = getSessionArguments(sessionId);
        
        StringBuilder negativeArg = new StringBuilder();
        
        alibabaAIService.generateDebateArgumentStream(
                sessionId,
                roundNumber,
                topic.getTitle(),
                "NEGATIVE",
                history,
                aiConfigs.get("negative"),
                "",
                (chunk, isComplete) -> {
                    negativeArg.append(chunk);
                    sendEvent(emitter, "ai_argument", Map.of(
                            "side", "NEGATIVE",
                            "chunk", isComplete ? negativeArg.toString() : chunk,
                            "complete", isComplete,
                            "round", roundNumber,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                }
        );

        // Store negative argument
        Argument negativeArgument = Argument.builder()
                .sessionId(sessionId)
                .roleId(negativeRole.getRoleId())
                .roundNumber(roundNumber)
                .argumentText(negativeArg.toString())
                .characterCount(negativeArg.length())
                .validationStatus(Argument.ValidationStatus.APPROVED)
                .isPreview(false)
                .submittedAt(LocalDateTime.now())
                .build();
        argumentMapper.insert(negativeArgument);

        Thread.sleep(delayMs / 2);

        // Check pause before negative moderator feedback
        if (checkPaused(sessionId)) {
            session.pause(String.format("round_%d_negative_after", roundNumber));
            debateSessionMapper.updateById(session);
            sendEvent(emitter, "debate_paused", Map.of("round", roundNumber, "position", "negative_after", "speaker", "MODERATOR"));
            return;
        }

        // Moderator summary for negative
        moderatorService.generateArgumentSummaryStream(
                negativeArgument.getArgumentId(),
                sessionId,
                language,
                (chunk, isComplete) -> {
                    sendEvent(emitter, "moderator_summary", Map.of(
                            "side", "NEGATIVE",
                            "chunk", chunk,
                            "complete", isComplete,
                            "round", roundNumber,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                }
        );

        // Moderator evaluation for negative
        moderatorService.generateArgumentEvaluationStream(
                negativeArgument.getArgumentId(),
                sessionId,
                language,
                (chunk, isComplete) -> {
                    sendEvent(emitter, "moderator_evaluation", Map.of(
                            "side", "NEGATIVE",
                            "chunk", chunk,
                            "complete", isComplete,
                            "round", roundNumber,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                }
        );

        // ========== PER-ROUND SCORING (v3) ==========
        // Score the round (both sides evaluated by all judges)
        Map<String, BigDecimal> roundScores = scoringService.scoreRound(sessionId, roundNumber, language);
        
        // Send round scores
        sendEvent(emitter, "round_scores_update", Map.of(
            "round", roundNumber,
            "affirmativeScore", roundScores.get("affirmativeScore"),
            "negativeScore", roundScores.get("negativeScore"),
            "timestamp", LocalDateTime.now().toString()
        ));
        
        // Get and send cumulative scores
        Map<String, BigDecimal> cumulativeScores = scoringService.getCumulativeScores(sessionId);
        sendEvent(emitter, "cumulative_scores_update", Map.of(
            "affirmativeTotal", cumulativeScores.get("affirmativeTotal"),
            "negativeTotal", cumulativeScores.get("negativeTotal"),
            "maxPossible", cumulativeScores.get("maxPossible"),
            "timestamp", LocalDateTime.now().toString()
        ));

        // Round complete
        sendEvent(emitter, "round_complete", Map.of("round", roundNumber, "timestamp", LocalDateTime.now().toString()));

        Thread.sleep(delayMs);
    }

    /**
     * Generate final judging sequence
     */
    private void generateJudgingSequence(Long sessionId, String language, SseEmitter emitter) {
        log.info("Generating judging sequence for session: {}", sessionId);

        sendEvent(emitter, "judging_start", Map.of("timestamp", LocalDateTime.now().toString()));

        // Get final scores
        Map<String, Object> finalScores = getCurrentScores(sessionId);
        BigDecimal affirmativeScore = (BigDecimal) finalScores.get("affirmativeScore");
        BigDecimal negativeScore = (BigDecimal) finalScores.get("negativeScore");

        // Determine winner
        DebateSession.Winner winner;
        if (affirmativeScore.compareTo(negativeScore) > 0) {
            winner = DebateSession.Winner.AFFIRMATIVE;
        } else if (negativeScore.compareTo(affirmativeScore) > 0) {
            winner = DebateSession.Winner.NEGATIVE;
        } else {
            winner = DebateSession.Winner.DRAW;
        }

        // Judge feedback (3 judges)
        for (int i = 1; i <= 3; i++) {
            final int judgeNum = i;
            moderatorService.generateJudgeFeedbackStream(
                    sessionId,
                    judgeNum,
                    language,
                    (chunk, isComplete) -> {
                        sendEvent(emitter, "judge_feedback", Map.of(
                                "judgeNumber", judgeNum,
                                "chunk", chunk,
                                "complete", isComplete,
                                "timestamp", LocalDateTime.now().toString()
                        ));
                    }
            );
        }

        // Send final scores
        sendEvent(emitter, "final_scores", finalScores);

        // Winner announcement
        moderatorService.generateWinnerAnnouncementStream(
                sessionId,
                winner.name(),
                language,
                (chunk, isComplete) -> {
                    sendEvent(emitter, "winner_announcement", Map.of(
                            "winner", winner.name(),
                            "chunk", chunk,
                            "complete", isComplete,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                }
        );

        // Complete session
        DebateSession session = debateSessionMapper.selectById(sessionId);
        session.complete(affirmativeScore, negativeScore, winner);
        debateSessionMapper.updateById(session);
    }

    /**
     * Resume from paused position
     */
    private void resumeFromPosition(Long sessionId, String language, SseEmitter emitter, String position) {
        log.info("Resuming session {} from position: {}", sessionId, position);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        session.resume();
        debateSessionMapper.updateById(session);

        // Parse position and continue
        if (position != null && position.startsWith("round_")) {
            DebateTopic topic = topicMapper.selectById(session.getTopicId());
            long delayMs = getDelayForSpeed(session.getAutoPlaySpeed());

            try {
                // Parse position format: round_{n}_affirmative_before, round_{n}_affirmative_after, etc.
                String[] parts = position.split("_");
                int round = Integer.parseInt(parts[1]);
                
                if (parts.length == 2) {
                    // Old format: round_{n} - resume from that round
                    for (int r = round; r <= 5; r++) {
                        generateRound(sessionId, r, language, emitter, delayMs);
                    }
                } else if (parts.length == 4) {
                    // New format: round_{n}_{side}_{timing}
                    String side = parts[2]; // "affirmative" or "negative"
                    String timing = parts[3]; // "before" or "after"
                    
                    // Resume from specific position within the round
                    resumeFromRoundPosition(sessionId, round, side, timing, language, emitter, delayMs);
                    
                    // Continue with remaining rounds
                    for (int r = round + 1; r <= 5; r++) {
                        generateRound(sessionId, r, language, emitter, delayMs);
                    }
                }
                
                generateJudgingSequence(sessionId, language, emitter);
                sendEvent(emitter, "debate_complete", Map.of("sessionId", sessionId));
                emitter.complete();
            } catch (Exception e) {
                log.error("Error resuming debate", e);
                sendError(emitter, "Resume failed: " + e.getMessage());
            }
        }
    }

    /**
     * Resume from specific position within a round
     */
    private void resumeFromRoundPosition(Long sessionId, int roundNumber, String side, String timing, 
                                         String language, SseEmitter emitter, long delayMs) throws InterruptedException {
        log.info("Resuming round {} from {} {}", roundNumber, side, timing);
        
        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());
        Map<String, Map<String, String>> aiConfigs = parseAiConfigs(session.getAiDebaterConfigs());
        Role affirmativeRole = getRole(sessionId, "AFFIRMATIVE");
        Role negativeRole = getRole(sessionId, "NEGATIVE");
        List<Argument> history = getSessionArguments(sessionId);
        
        // Determine where to resume based on position
        boolean skipAffirmative = false;
        boolean skipAffirmativeModeratorFeedback = false;
        boolean skipNegative = false;
        
        if (side.equals("affirmative")) {
            if (timing.equals("after")) {
                // Skip affirmative argument, resume at moderator feedback
                skipAffirmative = true;
            }
            // If timing is "before", start from affirmative argument
        } else if (side.equals("negative")) {
            // Skip both affirmative parts
            skipAffirmative = true;
            skipAffirmativeModeratorFeedback = true;
            
            if (timing.equals("after")) {
                // Also skip negative argument
                skipNegative = true;
            }
        }
        
        // Execute remaining parts of the round
        if (!skipAffirmative) {
            // Generate affirmative argument
            StringBuilder affirmativeArg = new StringBuilder();
            alibabaAIService.generateDebateArgumentStream(
                    sessionId, roundNumber, topic.getTitle(), "AFFIRMATIVE",
                    history, aiConfigs.get("affirmative"), "",
                    (chunk, isComplete) -> {
                        affirmativeArg.append(chunk);
                        sendEvent(emitter, "ai_argument", Map.of(
                                "side", "AFFIRMATIVE",
                                "chunk", isComplete ? affirmativeArg.toString() : chunk,
                                "complete", isComplete,
                                "round", roundNumber,
                                "timestamp", LocalDateTime.now().toString()
                        ));
                    }
            );
            
            Argument affirmativeArgument = Argument.builder()
                    .sessionId(sessionId).roleId(affirmativeRole.getRoleId())
                    .roundNumber(roundNumber).argumentText(affirmativeArg.toString())
                    .characterCount(affirmativeArg.length())
                    .validationStatus(Argument.ValidationStatus.APPROVED)
                    .isPreview(false).submittedAt(LocalDateTime.now()).build();
            argumentMapper.insert(affirmativeArgument);
            Thread.sleep(delayMs / 2);
            
            if (!skipAffirmativeModeratorFeedback) {
                moderatorService.generateArgumentSummaryStream(
                        affirmativeArgument.getArgumentId(), sessionId, language,
                        (chunk, isComplete) -> sendEvent(emitter, "moderator_summary", Map.of(
                                "side", "AFFIRMATIVE", "chunk", chunk, "complete", isComplete,
                                "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                        ))
                );
                moderatorService.generateArgumentEvaluationStream(
                        affirmativeArgument.getArgumentId(), sessionId, language,
                        (chunk, isComplete) -> sendEvent(emitter, "moderator_evaluation", Map.of(
                                "side", "AFFIRMATIVE", "chunk", chunk, "complete", isComplete,
                                "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                        ))
                );
                Thread.sleep(delayMs);
            }
        } else if (!skipAffirmativeModeratorFeedback) {
            // Affirmative argument exists, generate moderator feedback only
            Argument affirmativeArgument = getLastArgumentForRoundAndSide(sessionId, roundNumber, "AFFIRMATIVE");
            if (affirmativeArgument != null) {
                moderatorService.generateArgumentSummaryStream(
                        affirmativeArgument.getArgumentId(), sessionId, language,
                        (chunk, isComplete) -> sendEvent(emitter, "moderator_summary", Map.of(
                                "side", "AFFIRMATIVE", "chunk", chunk, "complete", isComplete,
                                "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                        ))
                );
                moderatorService.generateArgumentEvaluationStream(
                        affirmativeArgument.getArgumentId(), sessionId, language,
                        (chunk, isComplete) -> sendEvent(emitter, "moderator_evaluation", Map.of(
                                "side", "AFFIRMATIVE", "chunk", chunk, "complete", isComplete,
                                "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                        ))
                );
                Thread.sleep(delayMs);
            }
        }
        
        if (!skipNegative) {
            // Refresh history and generate negative argument
            history = getSessionArguments(sessionId);
            StringBuilder negativeArg = new StringBuilder();
            
            alibabaAIService.generateDebateArgumentStream(
                    sessionId, roundNumber, topic.getTitle(), "NEGATIVE",
                    history, aiConfigs.get("negative"), "",
                    (chunk, isComplete) -> {
                        negativeArg.append(chunk);
                        sendEvent(emitter, "ai_argument", Map.of(
                                "side", "NEGATIVE",
                                "chunk", isComplete ? negativeArg.toString() : chunk,
                                "complete", isComplete,
                                "round", roundNumber,
                                "timestamp", LocalDateTime.now().toString()
                        ));
                    }
            );
            
            Argument negativeArgument = Argument.builder()
                    .sessionId(sessionId).roleId(negativeRole.getRoleId())
                    .roundNumber(roundNumber).argumentText(negativeArg.toString())
                    .characterCount(negativeArg.length())
                    .validationStatus(Argument.ValidationStatus.APPROVED)
                    .isPreview(false).submittedAt(LocalDateTime.now()).build();
            argumentMapper.insert(negativeArgument);
            Thread.sleep(delayMs / 2);
            
            moderatorService.generateArgumentSummaryStream(
                    negativeArgument.getArgumentId(), sessionId, language,
                    (chunk, isComplete) -> sendEvent(emitter, "moderator_summary", Map.of(
                            "side", "NEGATIVE", "chunk", chunk, "complete", isComplete,
                            "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                    ))
            );
            moderatorService.generateArgumentEvaluationStream(
                    negativeArgument.getArgumentId(), sessionId, language,
                    (chunk, isComplete) -> sendEvent(emitter, "moderator_evaluation", Map.of(
                            "side", "NEGATIVE", "chunk", chunk, "complete", isComplete,
                            "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                    ))
            );
            
            // Score arguments
            Argument affirmativeArgument = getLastArgumentForRoundAndSide(sessionId, roundNumber, "AFFIRMATIVE");
            if (affirmativeArgument != null) {
                scoringService.scoreArgument(affirmativeArgument.getArgumentId(), sessionId);
            }
            scoringService.scoreArgument(negativeArgument.getArgumentId(), sessionId);
        } else {
            // Negative argument exists, generate moderator feedback only
            Argument negativeArgument = getLastArgumentForRoundAndSide(sessionId, roundNumber, "NEGATIVE");
            if (negativeArgument != null) {
                moderatorService.generateArgumentSummaryStream(
                        negativeArgument.getArgumentId(), sessionId, language,
                        (chunk, isComplete) -> sendEvent(emitter, "moderator_summary", Map.of(
                                "side", "NEGATIVE", "chunk", chunk, "complete", isComplete,
                                "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                        ))
                );
                moderatorService.generateArgumentEvaluationStream(
                        negativeArgument.getArgumentId(), sessionId, language,
                        (chunk, isComplete) -> sendEvent(emitter, "moderator_evaluation", Map.of(
                                "side", "NEGATIVE", "chunk", chunk, "complete", isComplete,
                                "round", roundNumber, "timestamp", LocalDateTime.now().toString()
                        ))
                );
            }
        }
        
        // Send scores update
        Map<String, Object> scores = getCurrentScores(sessionId);
        sendEvent(emitter, "scores_update", scores);
        sendEvent(emitter, "round_complete", Map.of("round", roundNumber, "timestamp", LocalDateTime.now().toString()));
        Thread.sleep(delayMs);
    }

    /**
     * Get last argument for a specific round and side
     */
    private Argument getLastArgumentForRoundAndSide(Long sessionId, int roundNumber, String side) {
        Role role = getRole(sessionId, side);
        if (role == null) {
            return null;
        }
        
        QueryWrapper<Argument> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("role_id", role.getRoleId());
        wrapper.eq("round_number", roundNumber);
        wrapper.eq("is_preview", false);
        wrapper.orderByDesc("submitted_at");
        wrapper.last("LIMIT 1");
        
        return argumentMapper.selectOne(wrapper);
    }

    /**
     * Skip to end - generate all remaining content without delays
     */
    public Map<String, Object> skipToEnd(Long sessionId) {
        log.info("Skipping to end for session: {}", sessionId);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        Map<String, Object> finalScores = getCurrentScores(sessionId);
        BigDecimal affirmativeScore = (BigDecimal) finalScores.get("affirmativeScore");
        BigDecimal negativeScore = (BigDecimal) finalScores.get("negativeScore");

        DebateSession.Winner winner;
        if (affirmativeScore.compareTo(negativeScore) > 0) {
            winner = DebateSession.Winner.AFFIRMATIVE;
        } else if (negativeScore.compareTo(affirmativeScore) > 0) {
            winner = DebateSession.Winner.NEGATIVE;
        } else {
            winner = DebateSession.Winner.DRAW;
        }

        session.complete(affirmativeScore, negativeScore, winner);
        debateSessionMapper.updateById(session);

        return Map.of(
                "status", "COMPLETED",
                "winner", winner.name(),
                "finalScores", finalScores
        );
    }

    /**
     * Pause debate
     */
    public Map<String, Object> pauseDebate(Long sessionId) {
        log.info("Pausing debate session: {}", sessionId);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        
        // Determine current position based on session state
        String currentPosition = "round_" + getCurrentRound(sessionId);
        
        session.pause(currentPosition);
        debateSessionMapper.updateById(session);

        return Map.of(
                "status", "PAUSED",
                "currentPosition", currentPosition
        );
    }

    /**
     * Resume debate from paused state
     */
    public Map<String, Object> resumeDebate(Long sessionId) {
        log.info("Resuming debate session: {}", sessionId);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        
        if (!session.isPaused()) {
            return Map.of(
                "status", "ERROR",
                "message", "Session is not paused"
            );
        }
        
        // Resume will be handled by the streaming endpoint
        // Just update the session state
        session.resume();
        debateSessionMapper.updateById(session);

        return Map.of(
                "status", "RESUMED",
                "currentPosition", session.getCurrentPosition()
        );
    }

    // ========== Helper Methods ==========

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

    private long getDelayForSpeed(DebateSession.AutoPlaySpeed speed) {
        if (speed == null) {
            speed = DebateSession.AutoPlaySpeed.NORMAL;
        }
        
        return switch (speed) {
            case FAST -> 1000L;
            case SLOW -> 5000L;
            default -> 3000L;
        };
    }

    private boolean checkPaused(Long sessionId) {
        DebateSession session = debateSessionMapper.selectById(sessionId);
        return session.isPaused();
    }

    private Role getRole(Long sessionId, String roleType) {
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("role_type", roleType);
        return roleMapper.selectOne(wrapper);
    }

    private List<Argument> getSessionArguments(Long sessionId) {
        QueryWrapper<Argument> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("is_preview", false);
        wrapper.orderByAsc("round_number", "submitted_at");
        return argumentMapper.selectList(wrapper);
    }

    private Map<String, Object> getCurrentScores(Long sessionId) {
        // Use per-round scoring (v3)
        Map<String, BigDecimal> cumulativeScores = scoringService.getCumulativeScores(sessionId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("affirmativeScore", cumulativeScores.get("affirmativeTotal"));
        result.put("negativeScore", cumulativeScores.get("negativeTotal"));
        result.put("sessionId", sessionId);

        return result;
    }

    private List<Argument> getArgumentsForSide(Long sessionId, String side) {
        Role role = getRole(sessionId, side);
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

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> parseAiConfigs(String aiDebaterConfigsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.readValue(aiDebaterConfigsJson, Map.class);
        } catch (Exception e) {
            log.error("Error parsing AI configs", e);
            // Return default configs
            Map<String, Map<String, String>> defaultConfigs = new HashMap<>();
            defaultConfigs.put("affirmative", Map.of("personality", "Analytical", "expertiseLevel", "Expert"));
            defaultConfigs.put("negative", Map.of("personality", "Passionate", "expertiseLevel", "Expert"));
            return defaultConfigs;
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", eventName, e);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
            emitter.completeWithError(new RuntimeException(message));
        } catch (Exception e) {
            log.error("Error sending error event", e);
        }
    }
}
