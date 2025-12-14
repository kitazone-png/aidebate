package com.aidebate.app.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.compiler.CodeGenerator.primary_return;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Alibaba AI Service
 * Simulates AI interactions for debate system
 * Note: In production, this would integrate with actual Alibaba Cloud API
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
public class AlibabaAIService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DashScopeChatModel chatModel;

    private final OllamaChatModel ollamaChatModel;


    @Value("${spring.ai.alibaba.model:qwen-max}")
    private String model;

    @Value("${spring.ai.alibaba.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${spring.ai.alibaba.max-tokens:2000}")
    private Integer maxTokens;

    @Autowired
    public AlibabaAIService(DashScopeChatModel chatModel,OllamaChatModel ollamaChatModel) {
        this.chatModel = chatModel;
        this.ollamaChatModel = ollamaChatModel;
    }

    /**
     * Generate debate topic from keywords
     */
    public Map<String, String> generateDebateTopic(String keywords) {
        log.info("Generating debate topic from keywords: {}", keywords);

        try {
            // Simulate AI response
            String response = simulateTopicGeneration(keywords);
            return parseTopicResponse(response);
        } catch (Exception e) {
            log.error("Error generating debate topic", e);
            Map<String, String> fallback = new HashMap<>();
            fallback.put("title", "Debate: " + keywords);
            fallback.put("description", "A debate topic generated from user input: " + keywords);
            return fallback;
        }
    }

    /**
     * Generate AI opponent argument
     */
    public String generateOpponentArgument(String topic, String side, int roundNumber,
                                           List<String> argumentHistory, Map<String, String> aiConfig) {
        log.info("Generating opponent argument for topic: {}, side: {}, round: {}", topic, side, roundNumber);

        String personality = aiConfig.getOrDefault("personality", "Analytical");
        String expertiseLevel = aiConfig.getOrDefault("expertiseLevel", "Expert");

        try {
            // Construct prompt
            String systemPrompt = buildOpponentSystemPrompt(topic, side, personality, expertiseLevel);
            String userPrompt = buildOpponentUserPrompt(topic, "", side, roundNumber, argumentHistory);

            // Call Qwen API with retry logic
            String argument = callQwenAPIWithRetry(systemPrompt, userPrompt, 3);

            // Apply character limit
            if (argument.length() > 500) {
                argument = argument.substring(0, 497) + "...";
            }
            return argument;
        } catch (Exception e) {
            log.error("Error generating opponent argument, using fallback", e);
            return getFallbackOpponentArgument(side);
        }
    }

    /**
     * Preview/suggest argument for user (renamed to simulateUserArgument)
     */
    public String simulateUserArgument(Long sessionId, int roundNumber, String topic, String side, List<String> argumentHistory, Map<String, String> aiConfig, String moderatorInstruction) {
        log.info("Generating user argument simulation for session: {}", sessionId);

        try {
            String personality = aiConfig.getOrDefault("personality", "Analytical");
            String expertiseLevel = aiConfig.getOrDefault("expertiseLevel", "Expert");

            // Construct simulation prompt
            String systemPrompt = buildSimulationSystemPrompt(topic, side, personality, expertiseLevel);
            String userPrompt = buildSimulationUserPrompt(topic, side, roundNumber, argumentHistory, moderatorInstruction);

            // Call Qwen API with retry logic
            String suggestion = callQwenAPIWithRetry(systemPrompt, userPrompt, 2);

            // Apply character limit
            if (suggestion.length() > 500) {
                suggestion = suggestion.substring(0, 497) + "...";
            }
            return suggestion;
        } catch (Exception e) {
            log.error("Error generating user argument simulation", e);
            return "";
        }
    }

    /**
     * Generate argument summary for moderator
     */
    public String generateArgumentSummary(String argumentText, String topic, String language) {
        log.info("Generating argument summary");

        try {
            String systemPrompt = buildModeratorSummaryPrompt(language);
            String userPrompt = String.format(
                "Topic: %s\n\nArgument to summarize:\n%s\n\nProvide a concise summary (max 200 characters) highlighting the main points.",
                topic, argumentText
            );

            String summary = callQwenAPIWithRetry(systemPrompt, userPrompt, 2);
            if (summary.length() > 200) {
                summary = summary.substring(0, 197) + "...";
            }
            return summary;
        } catch (Exception e) {
            log.error("Error generating argument summary", e);
            return "zh".equals(language) ? "论述已接收。" : "Argument received.";
        }
    }

    /**
     * Generate argument evaluation for moderator
     */
    public String generateArgumentEvaluation(String argumentText, String topic, List<String> argumentHistory, String language) {
        log.info("Generating argument evaluation");

        try {
            String systemPrompt = buildModeratorEvaluationPrompt(language);
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Topic: ").append(topic).append("\n\n");
            
            if (!argumentHistory.isEmpty()) {
                userPrompt.append("Previous arguments:\n");
                for (int i = 0; i < Math.min(3, argumentHistory.size()); i++) {
                    userPrompt.append("- ").append(argumentHistory.get(argumentHistory.size() - 1 - i)).append("\n");
                }
                userPrompt.append("\n");
            }
            
            userPrompt.append("Current argument to evaluate:\n").append(argumentText).append("\n\n");
            userPrompt.append("Provide a balanced evaluation (max 300 characters) considering logic, relevance, and persuasiveness.");

            String evaluation = callQwenAPIWithRetry(systemPrompt, userPrompt.toString(), 2);
            if (evaluation.length() > 300) {
                evaluation = evaluation.substring(0, 297) + "...";
            }
            return evaluation;
        } catch (Exception e) {
            log.error("Error generating argument evaluation", e);
            return "zh".equals(language) ? "论述具有良好的逻辑结构。" : "Argument shows good logical structure.";
        }
    }

    /**
     * Generate speaker announcement for moderator
     */
    public String generateSpeakerAnnouncement(String nextSpeaker, int roundNumber, String topic, List<String> recentModeratorMessages, String language) {
        log.info("Generating speaker announcement for: {}", nextSpeaker);

        try {
            String systemPrompt = buildModeratorAnnouncementPrompt(language);
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Topic: ").append(topic).append("\n");
            userPrompt.append("Current round: ").append(roundNumber).append("\n");
            userPrompt.append("Next speaker: ").append(nextSpeaker).append("\n\n");
            
            if (!recentModeratorMessages.isEmpty()) {
                userPrompt.append("Recent context: ").append(recentModeratorMessages.get(recentModeratorMessages.size() - 1)).append("\n\n");
            }
            
            userPrompt.append("Generate an announcement inviting the next speaker with guidance (max 200 characters).");

            String announcement = callQwenAPIWithRetry(systemPrompt, userPrompt.toString(), 2);
            if (announcement.length() > 200) {
                announcement = announcement.substring(0, 197) + "...";
            }
            return announcement;
        } catch (Exception e) {
            log.error("Error generating speaker announcement", e);
            String speaker = translateSide(nextSpeaker, language);
            return "zh".equals(language) ? 
                String.format("%s，请陈述您的论点。", speaker) :
                String.format("%s, please present your argument.", speaker);
        }
    }

    private String translateSide(String side, String language) {
        if ("zh".equals(language)) {
            return "AFFIRMATIVE".equals(side) ? "正方" : "反方";
        }
        return "AFFIRMATIVE".equals(side) ? "Affirmative" : "Negative";
    }

    // ========== Streaming Methods ==========

    /**
     * Functional interface for streaming callbacks
     */
    @FunctionalInterface
    public interface StreamCallback {
        void onChunk(String chunk, boolean isComplete);
    }

    /**
     * Generate argument summary with streaming
     */
    public void generateArgumentSummaryStream(String argumentText, String topic, String language, StreamCallback callback) {
        log.info("Generating argument summary with streaming");

        try {
            String systemPrompt = buildModeratorSummaryPrompt(language);
            String userPrompt = String.format(
                "Topic: %s\n\nArgument to summarize:\n%s\n\nProvide a concise summary (max 200 characters) highlighting the main points.",
                topic, argumentText
            );

            callQwenAPIStream(systemPrompt, userPrompt, callback, 200);
        } catch (Exception e) {
            log.error("Error generating argument summary stream", e);
            String fallback = "zh".equals(language) ? "论述已接收。" : "Argument received.";
            callback.onChunk(fallback, true);
        }
    }

    /**
     * Generate argument evaluation with streaming
     */
    public void generateArgumentEvaluationStream(String argumentText, String topic, List<String> argumentHistory, String language, StreamCallback callback) {
        log.info("Generating argument evaluation with streaming");

        try {
            String systemPrompt = buildModeratorEvaluationPrompt(language);
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Topic: ").append(topic).append("\n\n");
            
            if (!argumentHistory.isEmpty()) {
                userPrompt.append("Previous arguments:\n");
                for (int i = 0; i < Math.min(3, argumentHistory.size()); i++) {
                    userPrompt.append("- ").append(argumentHistory.get(argumentHistory.size() - 1 - i)).append("\n");
                }
                userPrompt.append("\n");
            }
            
            userPrompt.append("Current argument to evaluate:\n").append(argumentText).append("\n\n");
            userPrompt.append("Provide a balanced evaluation (max 300 characters) considering logic, relevance, and persuasiveness.");

            callQwenAPIStream(systemPrompt, userPrompt.toString(), callback, 300);
        } catch (Exception e) {
            log.error("Error generating argument evaluation stream", e);
            String fallback = "zh".equals(language) ? "论述具有良好的逻辑结构。" : "Argument shows good logical structure.";
            callback.onChunk(fallback, true);
        }
    }

    /**
     * Generate speaker announcement with streaming
     */
    public void generateSpeakerAnnouncementStream(String nextSpeaker, int roundNumber, String topic, List<String> recentModeratorMessages, String language, StreamCallback callback) {
        log.info("Generating speaker announcement with streaming for: {}", nextSpeaker);

        try {
            String systemPrompt = buildModeratorAnnouncementPrompt(language);
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Topic: ").append(topic).append("\n");
            
            // Include round format information
            String formatName = getRoundFormatName(roundNumber, language);
            String objective = getRoundObjective(roundNumber, language);
            userPrompt.append("Current round: ").append(roundNumber).append(" - ").append(formatName).append("\n");
            userPrompt.append("Round objective: ").append(objective).append("\n");
            userPrompt.append("Next speaker: ").append(nextSpeaker).append("\n\n");
            
            if (!recentModeratorMessages.isEmpty()) {
                userPrompt.append("Recent context: ").append(recentModeratorMessages.get(recentModeratorMessages.size() - 1)).append("\n\n");
            }
            
            userPrompt.append("Generate an announcement inviting the speaker and explaining the round format's strategic focus (max 300 characters).");

            callQwenAPIStream(systemPrompt, userPrompt.toString(), callback, 300);
        } catch (Exception e) {
            log.error("Error generating speaker announcement stream", e);
            String speaker = translateSide(nextSpeaker, language);
            String fallback = "zh".equals(language) ? 
                String.format("%s，请陈述您的论点。", speaker) :
                String.format("%s, please present your argument.", speaker);
            callback.onChunk(fallback, true);
        }
    }

    /**
     * Generate opponent argument with streaming
     */
    public void generateOpponentArgumentStream(String topic, String side, int roundNumber,
                                               List<String> argumentHistory, Map<String, String> aiConfig,
                                               StreamCallback callback) {
        log.info("Generating opponent argument with streaming for topic: {}, side: {}, round: {}", topic, side, roundNumber);

        String personality = aiConfig.getOrDefault("personality", "Analytical");
        String expertiseLevel = aiConfig.getOrDefault("expertiseLevel", "Expert");

        try {
            String systemPrompt = buildOpponentSystemPrompt(topic, side, personality, expertiseLevel);
            String userPrompt = buildOpponentUserPrompt(topic, "", side, roundNumber, argumentHistory);

            callQwenAPIStream(systemPrompt, userPrompt, callback, 500);
        } catch (Exception e) {
            log.error("Error generating opponent argument stream, using fallback", e);
            String fallback = getFallbackOpponentArgument(side);
            callback.onChunk(fallback, true);
        }
    }

    /**
     * Generate debate argument with streaming (for both affirmative and negative)
     * This is the unified method for AI vs AI debates
     */
    public void generateDebateArgumentStream(Long sessionId, int roundNumber, String topic, String side,
                                             List<com.aidebate.domain.model.Argument> argumentHistory,
                                             Map<String, String> aiConfig, String moderatorInstruction,
                                             StreamCallback callback) {
        log.info("Generating debate argument with streaming - Session: {}, Side: {}, Round: {}", sessionId, side, roundNumber);

        String personality = aiConfig.getOrDefault("personality", "Analytical");
        String expertiseLevel = aiConfig.getOrDefault("expertiseLevel", "Expert");

        try {
            // Convert argument history to text list
            List<String> historyTexts = argumentHistory.stream()
                    .map(com.aidebate.domain.model.Argument::getArgumentText)
                    .collect(Collectors.toList());

            String systemPrompt = buildDebateArgumentSystemPrompt(topic, side, personality, expertiseLevel);
            String userPrompt = buildDebateArgumentUserPrompt(topic, side, roundNumber, historyTexts, moderatorInstruction);

            callQwenAPIStream(systemPrompt, userPrompt, callback, 500);
        } catch (Exception e) {
            log.error("Error generating debate argument stream, using fallback", e);
            String fallback = getFallbackOpponentArgument(side);
            callback.onChunk(fallback, true);
        }
    }

    /**
     * Call Qwen API with streaming support
     * Fixed to send only incremental chunks, not accumulated text
     * 
     * @param maxLength Parameter retained for backward compatibility but not enforced as hard limit.
     *                  Prompt-level guidance ("最多500字") encourages conciseness while allowing complete content transmission.
     */
    private void callQwenAPIStream(String systemPrompt, String userPrompt, StreamCallback callback, int maxLength) {
        try {
            SystemMessage systemMessage = new SystemMessage(systemPrompt);
            UserMessage userMessage = new UserMessage(userPrompt);
            
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

            // Use streaming API with blocking to ensure sequential execution
            Flux<String> stream = ollamaChatModel.stream(prompt)
                .map(chatResponse -> chatResponse.getResult().getOutput().getText());

            StringBuilder accumulated = new StringBuilder();
            
            // Block and wait for streaming to complete
            stream.doOnNext(chunk -> {
                if (chunk != null && !chunk.isEmpty()) {
                    accumulated.append(chunk);
                    
                    // Send chunk regardless of accumulated length
                    // Frontend will handle display truncation for UX purposes
                    // This ensures complete content reaches the user
                    callback.onChunk(chunk, false);
                }
            })
            .doOnError(error -> {
                log.error("Error during streaming", error);
                // On error, mark as complete with current accumulated text
                callback.onChunk("", true);
            })
            .doOnComplete(() -> {
                // On complete, just signal completion without sending text again
                // The accumulated text has already been sent chunk by chunk
                callback.onChunk("", true);  // ✅ Signal completion only, no duplicate text
                log.debug("Streaming completed: {} characters", accumulated.length());
            })
            .blockLast(); // Block until streaming completes
            
        } catch (Exception e) {
            log.error("Error in streaming API call", e);
            throw new RuntimeException("Failed to stream AI response", e);
        }
    }
    public Map<String, Object> judgeArgument(String argumentText, String criteriaName,
                                             int maxScore, String criteriaDescription) {
        log.info("Judging argument for criterion: {}", criteriaName);

        try {
            String response = simulateJudgment(argumentText, criteriaName, maxScore);
            return parseJudgmentResponse(response, maxScore);
        } catch (Exception e) {
            log.error("Error in AI judge scoring", e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("score", maxScore * 0.6);
            fallback.put("feedback", "Unable to provide detailed feedback at this time.");
            return fallback;
        }
    }

    /**
     * Generate performance feedback for user
     */
    public Map<String, Object> generateFeedback(List<String> userArguments, String scoreBreakdown) {
        log.info("Generating performance feedback for user");

        try {
            String response = simulateFeedback(userArguments, scoreBreakdown);
            return parseFeedbackResponse(response);
        } catch (Exception e) {
            log.error("Error generating feedback", e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("logic_score", 70.0);
            fallback.put("persuasiveness_score", 70.0);
            fallback.put("fluency_score", 70.0);
            fallback.put("overall_assessment", "Good effort! Continue practicing to improve your debate skills.");
            fallback.put("improvements", List.of(
                    "Use more specific examples",
                    "Strengthen logical connections",
                    "Address opponent's points more directly"
            ));
            return fallback;
        }
    }

    // ========== Qwen API Integration Methods ==========

    /**
     * Call Qwen API with retry logic
     */
    private String callQwenAPIWithRetry(String systemPrompt, String userPrompt, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                log.debug("Calling Qwen API (attempt {}/{})", attempts + 1, maxRetries);
                return callQwenAPI(systemPrompt, userPrompt);
            } catch (Exception e) {
                lastException = e;
                attempts++;
                if (attempts < maxRetries) {
                    long waitTime = (long) Math.pow(2, attempts) * 1000; // Exponential backoff
                    log.warn("API call failed, retrying in {}ms (attempt {}/{})", waitTime, attempts, maxRetries);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
        }

        log.error("Failed to call Qwen API after {} attempts", maxRetries, lastException);
        throw new RuntimeException("Failed to generate AI response after " + maxRetries + " attempts", lastException);
    }

    /**
     * Call Qwen API using Spring AI
     */
    private String callQwenAPI(String systemPrompt, String userPrompt) {
        try {
            SystemMessage systemMessage = new SystemMessage(systemPrompt);
            UserMessage userMessage = new UserMessage(userPrompt);
            

            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

            var response = ollamaChatModel.call(prompt);
            // Get the content from the assistant message
            var output = response.getResult().getOutput();
            String content = output.getText();
            
            log.debug("Qwen API response received: {} characters", content.length());
            return content.trim();
        } catch (Exception e) {
            log.error("Error calling Qwen API", e);
            throw new RuntimeException("Failed to call Qwen API", e);
        }
    }

    /**
     * Build system prompt for opponent argument generation
     * Pure Chinese prompt to ensure consistent language output
     */
    private String buildOpponentSystemPrompt(String topic, String side, String personality, String expertiseLevel) {
        String sideText = "AFFIRMATIVE".equals(side) ? "正方" : "反方";
        return String.format(
            "你是一位具有%s水平知识和%s辩论风格的专业辩手。" +
            "你正在就\"%s\"这一主题辩论%s立场。\n\n" +
            "你的目标是提出令人信服、有证据支持的论点，同时保持尊重的语气。" +
            "考虑对手之前的论点并有策略地应对。" +
            "直接输出辩论内容，不要包含任何提示词、格式说明或元信息。",
            expertiseLevel, personality, topic, sideText
        );
    }

    /**
     * Build user prompt for opponent argument generation
     * Pure Chinese prompt for consistent output
     */
    private String buildOpponentUserPrompt(String topic, String description, String side, int roundNumber, List<String> argumentHistory) {
        StringBuilder prompt = new StringBuilder();
        String sideText = "AFFIRMATIVE".equals(side) ? "正方" : "反方";
        
        prompt.append("辩题：").append(topic).append("\n");
        if (description != null && !description.isEmpty()) {
            prompt.append("描述：").append(description).append("\n");
        }
        prompt.append("你的立场：").append(sideText).append("\n");
        prompt.append("当前回合：第").append(roundNumber).append("回合（共5回合）\n\n");

        if (argumentHistory != null && !argumentHistory.isEmpty()) {
            prompt.append("辩论历史：\n");
            for (int i = 0; i < argumentHistory.size(); i++) {
                prompt.append("第").append(i + 1).append("回合：").append(argumentHistory.get(i)).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("请为你的立场生成一个简洁、有说服力的论点。");
        prompt.append("最多500字。");
        prompt.append("专注于逻辑、证据，并回应对手的关键论点。");
        prompt.append("直接输出辩论内容，不要添加任何格式标记或说明文字。");

        return prompt.toString();
    }

    /**
     * Build system prompt for argument preview
     * Pure Chinese prompt for consistent output
     */
    private String buildPreviewSystemPrompt() {
        return "你是一位经验丰富的辩论教练，帮助辩手改进论点。" +
               "提供建设性的建议，增强论点的清晰度、说服力和逻辑结构。" +
               "直接输出建议内容，不要包含任何提示词或格式说明。";
    }

    /**
     * Build user prompt for argument preview
     * Pure Chinese prompt for consistent output
     */
    private String buildPreviewUserPrompt(String topic, String side, int roundNumber, String partialText) {
        StringBuilder prompt = new StringBuilder();
        String sideText = "AFFIRMATIVE".equals(side) ? "正方" : "反方";
        
        prompt.append("辩题：").append(topic).append("\n");
        prompt.append("立场：").append(sideText).append("\n");
        prompt.append("回合：第").append(roundNumber).append("回合\n\n");

        if (partialText == null || partialText.trim().isEmpty()) {
            prompt.append("辩手需要开始论证。");
            prompt.append("建议一个强有力的开场陈述，清楚地确立立场。");
        } else {
            prompt.append("当前草稿：").append(partialText).append("\n\n");
            prompt.append("增强或完善此论点，使其更具说服力和结构性。");
            prompt.append("保持辩手的预期方向。");
        }
        prompt.append("总共最多500字。");
        prompt.append("直接输出建议内容，不要添加任何格式标记。");

        return prompt.toString();
    }

    /**
     * Build system prompt for user argument simulation
     * Pure Chinese prompt for consistent output
     */
    private String buildSimulationSystemPrompt(String topic, String side, String personality, String expertiseLevel) {
        String sideText = "AFFIRMATIVE".equals(side) ? "正方" : "反方";
        return String.format(
            "你正在模拟一位具有%s水平知识和%s辩论风格的辩手。" +
            "你正在为主题\"%s\"的%s立场辩护。" +
            "生成一个引人注目的论点供用户审阅和提交。" +
            "直接输出论点内容，不要包含任何提示词或格式说明。",
            expertiseLevel, personality, topic, sideText
        );
    }

    /**
     * Build user prompt for user argument simulation
     * Pure Chinese prompt for consistent output
     */
    private String buildSimulationUserPrompt(String topic, String side, int roundNumber, List<String> argumentHistory, String moderatorInstruction) {
        StringBuilder prompt = new StringBuilder();
        String sideText = "AFFIRMATIVE".equals(side) ? "正方" : "反方";
        
        prompt.append("辩题：").append(topic).append("\n");
        prompt.append("你的立场：").append(sideText).append("\n");
        prompt.append("当前回合：第").append(roundNumber).append("回合（共5回合）\n\n");

        if (moderatorInstruction != null && !moderatorInstruction.isEmpty()) {
            prompt.append("主持人指导：").append(moderatorInstruction).append("\n\n");
        }

        if (argumentHistory != null && !argumentHistory.isEmpty()) {
            prompt.append("辩论历史：\n");
            for (int i = 0; i < Math.min(5, argumentHistory.size()); i++) {
                prompt.append("- ").append(argumentHistory.get(argumentHistory.size() - 1 - i)).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("为你的立场生成一个有说服力的论点。");
        prompt.append("最多500字。");
        prompt.append("专注于逻辑、证据，并回应对手的论点。");
        prompt.append("直接输出论点内容，不要添加任何格式标记或说明文字。");

        return prompt.toString();
    }

    /**
     * Build system prompt for moderator summary
     * Respects user's language preference
     */
    private String buildModeratorSummaryPrompt(String language) {
        if ("zh".equals(language)) {
            return "你是一位经验丰富的辩论主持人。你的任务是客观、简洁地总结辩手的发言要点，保持中立立场。" +
                   "直接输出总结内容，不要包含任何提示词或格式说明。最多200字。";
        } else {
            return "You are an experienced debate moderator. Your task is to objectively and concisely summarize the key points of the debater's statement, maintaining a neutral stance. " +
                   "Output the summary directly without any prompts or formatting instructions. Maximum 200 words.";
        }
    }

    /**
     * Build system prompt for moderator evaluation
     * Respects user's language preference
     */
    private String buildModeratorEvaluationPrompt(String language) {
        if ("zh".equals(language)) {
            return "你是一位公正的辩论主持人。提供建设性、平衡的评价，考虑论点的逻辑性、相关性和说服力。保持中立并提供有价值的反馈。" +
                   "直接输出评价内容，不要包含任何提示词或格式说明。最多300字。";
        } else {
            return "You are a fair debate moderator. Provide constructive and balanced evaluation, considering the logic, relevance, and persuasiveness of the arguments. Remain neutral and provide valuable feedback. " +
                   "Output the evaluation directly without any prompts or formatting instructions. Maximum 300 words.";
        }
    }

    /**
     * Build system prompt for moderator announcement
     * Respects user's language preference
     */
    private String buildModeratorAnnouncementPrompt(String language) {
        if ("zh".equals(language)) {
            return "你是一位专业的辩论主持人。宣布下一位发言者并提供简短的指导，帮助推进辩论。保持鼓励和建设性的语气。" +
                   "直接输出宣布内容，不要包含任何提示词或格式说明。最多300字。";
        } else {
            return "You are a professional debate moderator. Announce the next speaker and provide brief guidance to help advance the debate. Maintain an encouraging and constructive tone. " +
                   "Output the announcement directly without any prompts or formatting instructions. Maximum 300 words.";
        }
    }

    /**
     * Get round format name based on round number
     * Returns localized format name (e.g., "开场陈述" or "Opening Statements")
     */
    private String getRoundFormatName(int roundNumber, String language) {
        boolean isChinese = "zh".equals(language);
        switch (roundNumber) {
            case 1:
                return isChinese ? "开场陈述" : "Opening Statements";
            case 2:
                return isChinese ? "反驳" : "Rebuttals";
            case 3:
                return isChinese ? "交叉质询" : "Cross-Examination";
            case 4:
                return isChinese ? "辩护" : "Defense";
            case 5:
                return isChinese ? "总结陈词" : "Closing Arguments";
            default:
                return isChinese ? "辩论" : "Debate";
        }
    }

    /**
     * Get round objective based on round number
     * Returns localized objective description
     */
    private String getRoundObjective(int roundNumber, String language) {
        boolean isChinese = "zh".equals(language);
        switch (roundNumber) {
            case 1:
                return isChinese ? "阐明立场和核心论点" : "Present position and core arguments";
            case 2:
                return isChinese ? "反驳对方论点" : "Counter opposing arguments";
            case 3:
                return isChinese ? "质疑对方逻辑" : "Challenge opponent's logic";
            case 4:
                return isChinese ? "巩固己方立场" : "Strengthen your position";
            case 5:
                return isChinese ? "最后总结和说服" : "Final summary and persuasion";
            default:
                return isChinese ? "展示论证能力" : "Demonstrate argumentation skills";
        }
    }

    /**
     * Get round strategic guidance based on round number
     * Returns localized strategic guidance for debaters
     */
    private String getRoundGuidance(int roundNumber, String language) {
        boolean isChinese = "zh".equals(language);
        switch (roundNumber) {
            case 1:
                return isChinese ? "清晰表达你的主张，提供初步证据" : "Clearly state your claim with initial evidence";
            case 2:
                return isChinese ? "指出对方论据的弱点，强化己方立场" : "Point out weaknesses in opponent's reasoning";
            case 3:
                return isChinese ? "通过提问揭示对方观点的矛盾" : "Use questions to reveal contradictions";
            case 4:
                return isChinese ? "回应对方质疑，补充证据加强论点" : "Address challenges and reinforce arguments";
            case 5:
                return isChinese ? "综合全场辩论，做出有力的最终陈述" : "Synthesize the debate with powerful conclusion";
            default:
                return isChinese ? "展示你的辩论技巧" : "Demonstrate your debating skills";
        }
    }

    /**
     * Build system prompt for debate argument (AI vs AI)
     */
    private String buildDebateArgumentSystemPrompt(String topic, String side, String personality, String expertiseLevel) {
        return String.format(
            "你是一位具有%s水平知识和%s辩论风格的AI辩手。" +
            "你正在就\"%s\"这一主题辩论%s立场。" +
            "请生成一个令人信服、结构清晰的论点，展示批判性思维和说服力。" +
            "直接输出论点内容，不要包含任何提示词、格式说明或元信息。",
            expertiseLevel, personality, topic, side
        );
    }

    /**
     * Build user prompt for debate argument (AI vs AI)
     */
    private String buildDebateArgumentUserPrompt(String topic, String side, int roundNumber, List<String> argumentHistory, String moderatorInstruction) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("辩题：").append(topic).append("\n");
        prompt.append("你的立场：").append(side.equals("AFFIRMATIVE") ? "正方" : "反方").append("\n");
        prompt.append("当前回合：第").append(roundNumber).append("回合（共5回合）\n\n");

        // Add round format information
        String formatName = getRoundFormatName(roundNumber, "zh");
        String objective = getRoundObjective(roundNumber, "zh");
        String guidance = getRoundGuidance(roundNumber, "zh");
        prompt.append("本轮格式：").append(formatName).append(" - ").append(objective).append("\n");
        prompt.append("目标：").append(guidance).append("\n\n");

        if (moderatorInstruction != null && !moderatorInstruction.isEmpty()) {
            prompt.append("主持人指导：").append(moderatorInstruction).append("\n\n");
        }

        if (argumentHistory != null && !argumentHistory.isEmpty()) {
            prompt.append("辩论历史：\n");
            int startIdx = Math.max(0, argumentHistory.size() - 6); // Last 6 arguments
            for (int i = startIdx; i < argumentHistory.size(); i++) {
                String speaker = (i % 2 == 0) ? "正方" : "反方";
                prompt.append("[").append(speaker).append("] ").append(argumentHistory.get(i)).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("请为第").append(roundNumber).append("回合生成你的论点。");
        prompt.append("最多500字。");
        prompt.append("考虑对方的论点，建立你的立场。");
        prompt.append("专注于逻辑、证据和说服力。");
        prompt.append("直接输出辩论内容，不要添加任何格式标记或说明文字。");

        return prompt.toString();
    }

    /**
     * Get fallback opponent argument when API fails
     */
    private String getFallbackOpponentArgument(String side) {
        if ("AFFIRMATIVE".equalsIgnoreCase(side)) {
            return "I support this position based on compelling evidence and logical reasoning that demonstrates clear benefits.";
        } else {
            return "I oppose this position as the evidence suggests significant concerns that outweigh potential benefits.";
        }
    }

    /**
     * Evaluate round performance for per-round scoring
     * Each judge evaluates a side's performance in a round holistically
     */
    public Map<String, Object> evaluateRoundPerformance(
            String debaterSide,
            int roundNumber,
            String topic,
            String sideArgument,
            String opponentArgument,
            List<String> previousRoundContext,
            String language,
            int judgeNumber) {
        
        log.info("Evaluating round {} performance for {} side by Judge {}", roundNumber, debaterSide, judgeNumber);
        
        try {
            String systemPrompt = buildRoundEvaluationSystemPrompt(judgeNumber, debaterSide, roundNumber, language);
            String userPrompt = buildRoundEvaluationUserPrompt(
                topic, roundNumber, sideArgument, opponentArgument, previousRoundContext, language
            );
            
            String response = callQwenAPIWithRetry(systemPrompt, userPrompt, 2);
            return parseJudgmentResponse(response, 100);
            
        } catch (Exception e) {
            log.error("Error evaluating round performance", e);
            // Fallback score
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("score", 75.0);
            fallback.put("feedback", "zh".equals(language) ? 
                "论点有一定说服力，逻辑基本清晰。" : 
                "Arguments show reasonable persuasiveness with clear logic.");
            return fallback;
        }
    }
    
    /**
     * Build system prompt for round evaluation (per-round scoring)
     */
    private String buildRoundEvaluationSystemPrompt(int judgeNumber, String side, int roundNumber, String language) {
        String sideText = "AFFIRMATIVE".equals(side) ? 
            ("zh".equals(language) ? "正方" : "Affirmative") :
            ("zh".equals(language) ? "反方" : "Negative");
        
        String roundFormat = getRoundFormatName(roundNumber, language);
        
        if ("zh".equals(language)) {
            return String.format(
                "你是第%d号评委，正在评估%s在第%d回合（%s）的表现。\n\n" +
                "评分维度（0-100分）：\n" +
                "1. 论点质量（40%%）：逻辑性、证据充分性、推理严谨性\n" +
                "2. 反驳有效性（30%%）：针对对手观点的回应、反驳论证\n" +
                "3. 修辞影响力（20%%）：说服力、表达清晰度、沟通效果\n" +
                "4. 战略定位（10%%）：推进己方立场、削弱对手论点\n\n" +
                "请提供：\n" +
                "- 分数：0-100之间的数字\n" +
                "- 反馈：简洁说明评分理由\n\n" +
                "直接输出JSON格式：{\"score\": 85.5, \"feedback\": \"...\"}\n" +
                "不要包含任何其他文字或格式说明。",
                judgeNumber, sideText, roundNumber, roundFormat
            );
        } else {
            return String.format(
                "You are Judge %d evaluating %s's performance in Round %d (%s).\n\n" +
                "Scoring Dimensions (0-100):\n" +
                "1. Argument Quality (40%%): Logic, evidence, reasoning soundness\n" +
                "2. Rebuttal Effectiveness (30%%): Addressing opponent's points, counterarguments\n" +
                "3. Rhetorical Impact (20%%): Persuasiveness, clarity, communication\n" +
                "4. Strategic Positioning (10%%): Advancing own position, undermining opponent\n\n" +
                "Provide:\n" +
                "- Score: Number between 0-100\n" +
                "- Feedback: Concise reasoning for the score\n\n" +
                "Output JSON format only: {\"score\": 85.5, \"feedback\": \"...\"}\n" +
                "Do not include any other text or format instructions.",
                judgeNumber, sideText, roundNumber, roundFormat
            );
        }
    }
    
    /**
     * Build user prompt for round evaluation
     */
    private String buildRoundEvaluationUserPrompt(
            String topic,
            int roundNumber,
            String sideArgument,
            String opponentArgument,
            List<String> previousRoundContext,
            String language) {
        
        StringBuilder prompt = new StringBuilder();
        
        if ("zh".equals(language)) {
            prompt.append("辩题：").append(topic).append("\n\n");
            
            if (previousRoundContext != null && !previousRoundContext.isEmpty()) {
                prompt.append("前几回合背景：\n");
                for (String context : previousRoundContext) {
                    prompt.append("- ").append(context).append("\n");
                }
                prompt.append("\n");
            }
            
            prompt.append("本方论点：\n").append(sideArgument).append("\n\n");
            prompt.append("对方论点：\n").append(opponentArgument).append("\n\n");
            prompt.append("请基于以上信息对本方在本回合的表现进行评分。");
        } else {
            prompt.append("Topic: ").append(topic).append("\n\n");
            
            if (previousRoundContext != null && !previousRoundContext.isEmpty()) {
                prompt.append("Previous rounds context:\n");
                for (String context : previousRoundContext) {
                    prompt.append("- ").append(context).append("\n");
                }
                prompt.append("\n");
            }
            
            prompt.append("This side's argument:\n").append(sideArgument).append("\n\n");
            prompt.append("Opponent's argument:\n").append(opponentArgument).append("\n\n");
            prompt.append("Please evaluate this side's performance in this round based on the above information.");
        }
        
        return prompt.toString();
    }

    // ========== Legacy Simulation Methods (for other features) ==========

    private String simulateTopicGeneration(String keywords) {
        return String.format("{\"title\": \"Should %s be regulated?\", \"description\": \"This debate explores the implications and necessity of regulating %s in modern society.\"}", 
                keywords, keywords);
    }

    private String simulateJudgment(String argumentText, String criteriaName, int maxScore) {
        // Simulate scoring based on argument length and structure
        int baseScore = (int) (maxScore * 0.7); // 70% base
        int lengthBonus = argumentText.length() > 200 ? (int) (maxScore * 0.1) : 0;
        int score = Math.min(maxScore, baseScore + lengthBonus);
        
        return String.format("{\"score\": %d, \"feedback\": \"Strong %s with good structure and evidence.\"}", 
                score, criteriaName.toLowerCase());
    }

    private String simulateFeedback(List<String> userArguments, String scoreBreakdown) {
        int avgLength = userArguments.stream().mapToInt(String::length).sum() / Math.max(1, userArguments.size());
        
        return String.format(
            "{\"logic_score\": 75, \"persuasiveness_score\": 72, \"fluency_score\": 78, " +
            "\"overall_assessment\": \"Good performance with solid arguments. Your average argument length of %d characters shows engagement.\", " +
            "\"improvements\": [\"Use more specific examples\", \"Strengthen logical connections\", \"Address counterarguments more directly\"]}",
            avgLength
        );
    }

    // ========== Response Parsing Methods ==========

    private Map<String, String> parseTopicResponse(String response) {
        try {
            String jsonStr = extractJSON(response);
            JsonNode node = objectMapper.readTree(jsonStr);
            Map<String, String> result = new HashMap<>();
            result.put("title", node.get("title").asText());
            result.put("description", node.get("description").asText());
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse topic response, using fallback", e);
            Map<String, String> result = new HashMap<>();
            result.put("title", "Generated Topic");
            result.put("description", response.length() > 500 ? response.substring(0, 500) : response);
            return result;
        }
    }

    private Map<String, Object> parseJudgmentResponse(String response, int maxScore) {
        try {
            String jsonStr = extractJSON(response);
            JsonNode node = objectMapper.readTree(jsonStr);
            Map<String, Object> result = new HashMap<>();
            result.put("score", node.get("score").asDouble());
            result.put("feedback", node.get("feedback").asText());
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse judgment response", e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("score", maxScore * 0.7);
            fallback.put("feedback", "Good argument with room for improvement.");
            return fallback;
        }
    }

    private Map<String, Object> parseFeedbackResponse(String response) {
        try {
            String jsonStr = extractJSON(response);
            JsonNode node = objectMapper.readTree(jsonStr);
            Map<String, Object> result = new HashMap<>();
            result.put("logic_score", node.get("logic_score").asDouble());
            result.put("persuasiveness_score", node.get("persuasiveness_score").asDouble());
            result.put("fluency_score", node.get("fluency_score").asDouble());
            result.put("overall_assessment", node.get("overall_assessment").asText());
            
            List<String> improvements = objectMapper.convertValue(
                    node.get("improvements"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            result.put("improvements", improvements);
            
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse feedback response", e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("logic_score", 70.0);
            fallback.put("persuasiveness_score", 70.0);
            fallback.put("fluency_score", 70.0);
            fallback.put("overall_assessment", "Good performance overall.");
            fallback.put("improvements", List.of("Keep practicing", "Study more examples"));
            return fallback;
        }
    }

    private String extractJSON(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end);
            }
        }
        return trimmed;
    }
}
