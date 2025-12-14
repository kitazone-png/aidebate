package com.aidebate.app.service;

import com.aidebate.domain.model.Argument;
import com.aidebate.domain.model.DebateSession;
import com.aidebate.domain.model.DebateTopic;
import com.aidebate.domain.model.ModeratorMessage;
import com.aidebate.domain.model.Role;
import com.aidebate.infrastructure.mapper.ArgumentMapper;
import com.aidebate.infrastructure.mapper.DebateSessionMapper;
import com.aidebate.infrastructure.mapper.DebateTopicMapper;
import com.aidebate.infrastructure.mapper.ModeratorMessageMapper;
import com.aidebate.infrastructure.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Moderator Service
 * Generates moderator messages for debate flow
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModeratorService {

    private final DebateSessionMapper debateSessionMapper;
    private final DebateTopicMapper topicMapper;
    private final ModeratorMessageMapper moderatorMessageMapper;
    private final ArgumentMapper argumentMapper;
    private final RoleMapper roleMapper;
    private final AlibabaAIService alibabaAIService;

    /**
     * Generate welcome message when session starts
     */
    public Map<String, Object> generateWelcomeMessage(Long sessionId, String language) {
        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());
        
        String content;
        
        if ("zh".equals(language)) {
            content = String.format("欢迎参加关于\"%s\"的辩论。让我们开始第1回合。", 
                topic.getTitle());
        } else {
            content = String.format("Welcome to the debate on \"%s\". Let's begin Round 1.", 
                topic.getTitle());
        }
        
        return createMessage("session_start", content);
    }

    /**
     * Generate round start message
     */
    public Map<String, Object> generateRoundStartMessage(int roundNumber, String speaker, String language) {
        String content;
        
        if ("zh".equals(language)) {
            content = String.format("第%d回合开始。%s,请陈述你的论点。", roundNumber, speaker);
        } else {
            content = String.format("Round %d begins. %s, please present your argument.", roundNumber, speaker);
        }
        
        return createMessage("round_start", content);
    }

    /**
     * Generate turn transition message
     */
    public Map<String, Object> generateTurnTransitionMessage(String speaker, String language) {
        String content;
        
        if ("zh".equals(language)) {
            content = String.format("论点已收到。现在%s,请回应。", speaker);
        } else {
            content = String.format("Argument received. Now %s, please respond.", speaker);
        }
        
        return createMessage("turn_transition", content);
    }

    /**
     * Generate round end message
     */
    public Map<String, Object> generateRoundEndMessage(int roundNumber, String language) {
        String content;
        
        if ("zh".equals(language)) {
            content = String.format("第%d回合结束。评委正在评分...", roundNumber);
        } else {
            content = String.format("Round %d complete. Judges are scoring...", roundNumber);
        }
        
        return createMessage("round_end", content);
    }

    /**
     * Generate session end message
     */
    public Map<String, Object> generateSessionEndMessage(String winner, String language) {
        String content;
        
        if ("zh".equals(language)) {
            content = String.format("辩论结束。最终获胜者:%s", winner);
        } else {
            content = String.format("Debate concluded. Final winner: %s", winner);
        }
        
        return createMessage("session_end", content);
    }

    /**
     * Generate phase change message
     */
    public Map<String, Object> generatePhaseChangeMessage(String phase, String language) {
        String content;
        
        if ("zh".equals(language)) {
            content = String.format("进入阶段:%s", phase);
        } else {
            content = String.format("Entering phase: %s", phase);
        }
        
        return createMessage("phase_change", content);
    }

    // ========== Helper Methods ==========

    private Map<String, Object> createMessage(String type, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("content", content);
        message.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return message;
    }

    private String translateSide(String side, String language) {
        if ("zh".equals(language)) {
            return "AFFIRMATIVE".equals(side) ? "正方" : "反方";
        } else {
            return "AFFIRMATIVE".equals(side) ? "Affirmative" : "Negative";
        }
    }

    /**
     * Generate argument summary using AI
     */
    @Transactional
    public Map<String, Object> generateArgumentSummary(Long argumentId, Long sessionId, String language) {
        log.info("Generating argument summary for argument: {}", argumentId);

        Argument argument = argumentMapper.selectById(argumentId);
        if (argument == null) {
            throw new RuntimeException("Argument not found: " + argumentId);
        }

        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());

        // Generate summary using AI
        String summary = alibabaAIService.generateArgumentSummary(
                argument.getArgumentText(),
                topic.getTitle(),
                language
        );

        // Store moderator message
        ModeratorMessage message = ModeratorMessage.builder()
                .sessionId(sessionId)
                .argumentId(argumentId)
                .roundNumber(argument.getRoundNumber())
                .messageType(ModeratorMessage.MessageType.SUMMARY)
                .content(summary)
                .speakerSide(determineSpeakerSide(argument, session))
                .createdAt(LocalDateTime.now())
                .build();

        moderatorMessageMapper.insert(message);

        return createMessageResponse(message);
    }

    /**
     * Generate argument evaluation using AI
     */
    @Transactional
    public Map<String, Object> generateArgumentEvaluation(Long argumentId, Long sessionId, String language) {
        log.info("Generating argument evaluation for argument: {}", argumentId);

        Argument argument = argumentMapper.selectById(argumentId);
        if (argument == null) {
            throw new RuntimeException("Argument not found: " + argumentId);
        }

        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());

        // Get argument history for context
        List<Argument> history = getArgumentHistory(sessionId, argument.getRoundNumber());

        // Generate evaluation using AI
        String evaluation = alibabaAIService.generateArgumentEvaluation(
                argument.getArgumentText(),
                topic.getTitle(),
                history.stream().map(Argument::getArgumentText).collect(Collectors.toList()),
                language
        );

        // Store moderator message
        ModeratorMessage message = ModeratorMessage.builder()
                .sessionId(sessionId)
                .argumentId(argumentId)
                .roundNumber(argument.getRoundNumber())
                .messageType(ModeratorMessage.MessageType.EVALUATION)
                .content(evaluation)
                .speakerSide(determineSpeakerSide(argument, session))
                .createdAt(LocalDateTime.now())
                .build();

        moderatorMessageMapper.insert(message);

        return createMessageResponse(message);
    }

    /**
     * Generate speaker announcement with instructions
     */
    @Transactional
    public Map<String, Object> generateSpeakerAnnouncement(String nextSpeaker, int roundNumber, Long sessionId, String language) {
        log.info("Generating speaker announcement for next speaker: {} in round {}", nextSpeaker, roundNumber);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());

        // Get recent moderator messages for context
        List<ModeratorMessage> recentMessages = getRecentModeratorMessages(sessionId, roundNumber);

        // Generate announcement with instructions using AI
        String announcement = alibabaAIService.generateSpeakerAnnouncement(
                nextSpeaker,
                roundNumber,
                topic.getTitle(),
                recentMessages.stream().map(ModeratorMessage::getContent).collect(Collectors.toList()),
                language
        );

        // Store moderator message
        ModeratorMessage message = ModeratorMessage.builder()
                .sessionId(sessionId)
                .argumentId(null)
                .roundNumber(roundNumber)
                .messageType(ModeratorMessage.MessageType.ANNOUNCEMENT)
                .content(announcement)
                .nextSpeaker(nextSpeaker)
                .createdAt(LocalDateTime.now())
                .build();

        moderatorMessageMapper.insert(message);

        return createMessageResponse(message);
    }

    /**
     * Get moderator messages for a session
     */
    public List<Map<String, Object>> getModeratorMessages(Long sessionId, Integer roundNumber) {
        QueryWrapper<ModeratorMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        if (roundNumber != null) {
            wrapper.eq("round_number", roundNumber);
        }
        wrapper.orderByAsc("created_at");

        List<ModeratorMessage> messages = moderatorMessageMapper.selectList(wrapper);
        return messages.stream().map(this::createMessageResponse).collect(Collectors.toList());
    }

    /**
     * Build moderator context for AI generation
     */
    public Map<String, Object> buildModeratorContext(Long sessionId, int roundNumber) {
        List<ModeratorMessage> messages = getRecentModeratorMessages(sessionId, roundNumber);
        
        Map<String, Object> context = new HashMap<>();
        context.put("summaries", messages.stream()
                .filter(m -> m.getMessageType() == ModeratorMessage.MessageType.SUMMARY)
                .map(ModeratorMessage::getContent)
                .collect(Collectors.toList()));
        context.put("evaluations", messages.stream()
                .filter(m -> m.getMessageType() == ModeratorMessage.MessageType.EVALUATION)
                .map(ModeratorMessage::getContent)
                .collect(Collectors.toList()));
        context.put("latestAnnouncement", messages.stream()
                .filter(m -> m.getMessageType() == ModeratorMessage.MessageType.ANNOUNCEMENT)
                .reduce((first, second) -> second)
                .map(ModeratorMessage::getContent)
                .orElse(""));
        
        return context;
    }

    // ========== Helper Methods ==========

    private List<Argument> getArgumentHistory(Long sessionId, int upToRound) {
        QueryWrapper<Argument> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("is_preview", false);
        wrapper.le("round_number", upToRound);
        wrapper.orderByAsc("round_number");
        wrapper.orderByAsc("submitted_at");
        return argumentMapper.selectList(wrapper);
    }

    private List<ModeratorMessage> getRecentModeratorMessages(Long sessionId, int roundNumber) {
        QueryWrapper<ModeratorMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.le("round_number", roundNumber);
        wrapper.orderByDesc("created_at");
        wrapper.last("LIMIT 10"); // Get last 10 messages for context
        return moderatorMessageMapper.selectList(wrapper);
    }

    private String determineSpeakerSide(Argument argument, DebateSession session) {
        // Determine which side made this argument based on role
        Role role = roleMapper.selectById(argument.getRoleId());
        if (role != null && role.getRoleType() != null) {
            return role.getRoleType().name();
        }
        // Fallback to AFFIRMATIVE if unable to determine
        return "AFFIRMATIVE";
    }

    private Map<String, Object> createMessageResponse(ModeratorMessage message) {
        Map<String, Object> response = new HashMap<>();
        response.put("messageId", message.getMessageId());
        response.put("type", message.getMessageType().name().toLowerCase());
        response.put("content", message.getContent());
        response.put("roundNumber", message.getRoundNumber());
        response.put("speakerSide", message.getSpeakerSide());
        response.put("nextSpeaker", message.getNextSpeaker());
        response.put("timestamp", message.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return response;
    }

    // ========== Streaming Methods ==========

    /**
     * Generate argument summary with streaming
     */
    @Transactional
    public void generateArgumentSummaryStream(Long argumentId, Long sessionId, String language, 
                                              AlibabaAIService.StreamCallback callback) {
        log.info("Generating argument summary with streaming for argument: {}", argumentId);

        Argument argument = argumentMapper.selectById(argumentId);
        if (argument == null) {
            throw new RuntimeException("Argument not found: " + argumentId);
        }

        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());

        // Storage for accumulating streamed content
        StringBuilder contentAccumulator = new StringBuilder();

        // Wrap callback to store the final message
        AlibabaAIService.StreamCallback wrappedCallback = (chunk, isComplete) -> {
            // Accumulate chunks (don't reset!)
            if (!isComplete && chunk != null && !chunk.isEmpty()) {
                contentAccumulator.append(chunk);
            }
            
            // Forward to original callback
            callback.onChunk(chunk, isComplete);
            
            // Store in database when complete
            if (isComplete) {
                ModeratorMessage message = ModeratorMessage.builder()
                        .sessionId(sessionId)
                        .argumentId(argumentId)
                        .roundNumber(argument.getRoundNumber())
                        .messageType(ModeratorMessage.MessageType.SUMMARY)
                        .content(contentAccumulator.toString())
                        .speakerSide(determineSpeakerSide(argument, session))
                        .createdAt(LocalDateTime.now())
                        .build();
                moderatorMessageMapper.insert(message);
            }
        };

        // Generate summary using AI with streaming
        alibabaAIService.generateArgumentSummaryStream(
                argument.getArgumentText(),
                topic.getTitle(),
                language,
                wrappedCallback
        );
    }

    /**
     * Generate argument evaluation with streaming
     */
    @Transactional
    public void generateArgumentEvaluationStream(Long argumentId, Long sessionId, String language,
                                                 AlibabaAIService.StreamCallback callback) {
        log.info("Generating argument evaluation with streaming for argument: {}", argumentId);

        Argument argument = argumentMapper.selectById(argumentId);
        if (argument == null) {
            throw new RuntimeException("Argument not found: " + argumentId);
        }

        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());

        // Get argument history for context
        List<Argument> history = getArgumentHistory(sessionId, argument.getRoundNumber());

        // Storage for accumulating streamed content
        StringBuilder contentAccumulator = new StringBuilder();

        // Wrap callback to store the final message
        AlibabaAIService.StreamCallback wrappedCallback = (chunk, isComplete) -> {
            // Accumulate chunks (don't reset!)
            if (!isComplete && chunk != null && !chunk.isEmpty()) {
                contentAccumulator.append(chunk);
            }
            
            // Forward to original callback
            callback.onChunk(chunk, isComplete);
            
            // Store in database when complete
            if (isComplete) {
                ModeratorMessage message = ModeratorMessage.builder()
                        .sessionId(sessionId)
                        .argumentId(argumentId)
                        .roundNumber(argument.getRoundNumber())
                        .messageType(ModeratorMessage.MessageType.EVALUATION)
                        .content(contentAccumulator.toString())
                        .speakerSide(determineSpeakerSide(argument, session))
                        .createdAt(LocalDateTime.now())
                        .build();
                moderatorMessageMapper.insert(message);
            }
        };

        // Generate evaluation using AI with streaming
        alibabaAIService.generateArgumentEvaluationStream(
                argument.getArgumentText(),
                topic.getTitle(),
                history.stream().map(Argument::getArgumentText).collect(Collectors.toList()),
                language,
                wrappedCallback
        );
    }

    /**
     * Generate speaker announcement with streaming
     */
    @Transactional
    public void generateSpeakerAnnouncementStream(String nextSpeaker, int roundNumber, Long sessionId, 
                                                  String language, AlibabaAIService.StreamCallback callback) {
        log.info("Generating speaker announcement with streaming for next speaker: {} in round {}", nextSpeaker, roundNumber);

        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());

        // Get recent moderator messages for context
        List<ModeratorMessage> recentMessages = getRecentModeratorMessages(sessionId, roundNumber);

        // Storage for accumulating streamed content
        StringBuilder contentAccumulator = new StringBuilder();

        // Wrap callback to store the final message
        AlibabaAIService.StreamCallback wrappedCallback = (chunk, isComplete) -> {
            // Accumulate chunks (don't reset!)
            if (!isComplete && chunk != null && !chunk.isEmpty()) {
                contentAccumulator.append(chunk);
            }
            
            // Forward to original callback
            callback.onChunk(chunk, isComplete);
            
            // Store in database when complete
            if (isComplete) {
                ModeratorMessage message = ModeratorMessage.builder()
                        .sessionId(sessionId)
                        .argumentId(null)
                        .roundNumber(roundNumber)
                        .messageType(ModeratorMessage.MessageType.ANNOUNCEMENT)
                        .content(contentAccumulator.toString())
                        .nextSpeaker(nextSpeaker)
                        .createdAt(LocalDateTime.now())
                        .build();
                moderatorMessageMapper.insert(message);
            }
        };

        // Generate announcement with instructions using AI with streaming
        alibabaAIService.generateSpeakerAnnouncementStream(
                nextSpeaker,
                roundNumber,
                topic.getTitle(),
                recentMessages.stream().map(ModeratorMessage::getContent).collect(Collectors.toList()),
                language,
                wrappedCallback
        );
    }

    /**
     * Generate organizer rules announcement with streaming
     */
    public void generateOrganizerRulesStream(Long sessionId, String language, AlibabaAIService.StreamCallback callback) {
        log.info("Generating organizer rules with streaming for session: {}", sessionId);

        String rules;
        if ("zh".equals(language)) {
            rules = "欢迎参加本次辩论赛!本次辩论将进行5轮,每轮双方各陈述论点。评委将根据逻辑性、说服力和表达流畅度评分。请各位辩手尊重规则,展现最佳表现。";
        } else {
            rules = "Welcome to this debate! This debate will consist of 5 rounds, with each side presenting arguments in each round. Judges will score based on logic, persuasiveness, and fluency. Please respect the rules and demonstrate your best performance.";
        }

        // Send as immediate complete message
        callback.onChunk(rules, true);
    }

    /**
     * Generate debate introduction with streaming
     */
    public void generateDebateIntroductionStream(Long sessionId, String topicTitle, String language, AlibabaAIService.StreamCallback callback) {
        log.info("Generating debate introduction with streaming for topic: {}", topicTitle);

        String introduction;
        if ("zh".equals(language)) {
            introduction = String.format("今天的辩题是:'%s'。双方将围绕这一话题展开精彩辩论。让我们以开放的心态倾听双方观点,见证思想的碰撞。现在,让我们开始!", topicTitle);
        } else {
            introduction = String.format("Today's debate topic is: '%s'. Both sides will engage in a wonderful debate on this issue. Let us listen to both perspectives with an open mind and witness the clash of ideas. Now, let's begin!", topicTitle);
        }

        // Send as immediate complete message
        callback.onChunk(introduction, true);
    }

    /**
     * Generate judge feedback with streaming
     */
    public void generateJudgeFeedbackStream(Long sessionId, int judgeNumber, String language, AlibabaAIService.StreamCallback callback) {
        log.info("Generating judge {} feedback with streaming for session: {}", judgeNumber, sessionId);

        String feedback;
        if ("zh".equals(language)) {
            feedback = String.format("评委%d: 双方辩手都展现了出色的辩论技巧。论点清晰,逻辑严谨,证据充分。这是一场精彩的辩论。", judgeNumber);
        } else {
            feedback = String.format("Judge %d: Both debaters demonstrated excellent debate skills. Arguments were clear, logic was rigorous, and evidence was sufficient. This was an excellent debate.", judgeNumber);
        }

        // Send as immediate complete message
        callback.onChunk(feedback, true);
    }

    /**
     * Generate winner announcement with streaming
     */
    public void generateWinnerAnnouncementStream(Long sessionId, String winner, String language, AlibabaAIService.StreamCallback callback) {
        log.info("Generating winner announcement with streaming: {}", winner);

        String winnerLabel = translateSide(winner, language);
        String announcement;
        
        if ("DRAW".equals(winner)) {
            if ("zh".equals(language)) {
                announcement = "经过激烈的辩论和公正的评判,本场辩论结果为平局。双方都展现了卓越的辩论能力,恭喜双方!";
            } else {
                announcement = "After intense debate and fair judging, this debate ends in a draw. Both sides demonstrated excellent debate skills. Congratulations to both sides!";
            }
        } else {
            if ("zh".equals(language)) {
                announcement = String.format("经过激烈的辩论和公正的评判,本场辩论的获胜方是:%s。恭喜获胜方,也感谢双方的精彩表现!", winnerLabel);
            } else {
                announcement = String.format("After intense debate and fair judging, the winner of this debate is: %s. Congratulations to the winner, and thank you both for the excellent performance!", winnerLabel);
            }
        }

        // Send as immediate complete message
        callback.onChunk(announcement, true);
    }
}