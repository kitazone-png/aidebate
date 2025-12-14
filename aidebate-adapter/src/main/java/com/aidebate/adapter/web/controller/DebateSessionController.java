package com.aidebate.adapter.web.controller;

import com.aidebate.app.service.DebateSessionService;
import com.aidebate.app.service.DebateOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Debate Session Controller
 * Handles all debate session operations
 *
 * @author AI Debate Team
 */
@Slf4j
@RestController
@RequestMapping("/api/debates")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DebateSessionController {

    private final DebateSessionService debateSessionService;
    private final DebateOrchestrationService debateOrchestrationService;

    /**
     * Initialize a new debate session for AI vs AI debate
     * POST /api/debates/init
     * Body: {topicId, userId, aiConfigs: {affirmative: {personality, expertiseLevel}, negative: {personality, expertiseLevel}}, autoPlaySpeed}
     */
    @PostMapping("/init")
    public Map<String, Object> initializeSession(@RequestBody Map<String, Object> request) {
        log.info("Initializing AI vs AI debate session: {}", request);
        
        Long topicId = Long.parseLong(request.get("topicId").toString());
        Long userId = Long.parseLong(request.getOrDefault("userId", "1").toString());
        String autoPlaySpeed = request.getOrDefault("autoPlaySpeed", "NORMAL").toString();
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> aiConfigs = (Map<String, Map<String, String>>) request.getOrDefault("aiConfigs", 
                Map.of(
                    "affirmative", Map.of("personality", "Analytical", "expertiseLevel", "Expert"),
                    "negative", Map.of("personality", "Passionate", "expertiseLevel", "Expert")
                ));
        
        return debateSessionService.initializeSession(topicId, userId, aiConfigs, autoPlaySpeed);
    }

    /**
     * Start a debate session
     * POST /api/debates/{sessionId}/start
     */
    @PostMapping("/{sessionId}/start")
    public Map<String, Object> startSession(@PathVariable Long sessionId) {
        log.info("Starting debate session: {}", sessionId);
        return debateSessionService.startSession(sessionId);
    }

    /**
     * Get session details
     * GET /api/debates/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public Map<String, Object> getSessionDetails(@PathVariable Long sessionId) {
        return debateSessionService.getSessionDetails(sessionId);
    }

    /**
     * Submit user argument - DEPRECATED
     * This endpoint is no longer supported in AI vs AI mode
     * POST /api/debates/{sessionId}/submit-argument
     * Body: {argumentText, roundNumber}
     */
    @Deprecated
    @PostMapping("/{sessionId}/submit-argument")
    public Map<String, Object> submitArgument(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request) {
        
        log.warn("Deprecated endpoint called: submit-argument for session: {}", sessionId);
        return Map.of(
            "error", "This endpoint is deprecated in AI vs AI mode",
            "message", "User arguments are no longer supported. Use automated debate flow instead."
        );
    }

    /**
     * Simulate user argument (AI suggestion) - DEPRECATED
     * This endpoint is no longer supported in AI vs AI mode
     * POST /api/debates/{sessionId}/simulate-argument
     * Body: {roundNumber}
     */
    @Deprecated
    @PostMapping("/{sessionId}/simulate-argument")
    public Map<String, Object> simulateArgument(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request) {
        
        log.warn("Deprecated endpoint called: simulate-argument for session: {}", sessionId);
        return Map.of(
            "error", "This endpoint is deprecated in AI vs AI mode",
            "message", "Simulation is no longer needed. Use automated debate flow instead."
        );
    }

    /**
     * Get moderator messages
     * GET /api/debates/{sessionId}/moderator-messages
     */
    @GetMapping("/{sessionId}/moderator-messages")
    public Map<String, Object> getModeratorMessages(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Integer roundNumber) {
        
        log.info("Getting moderator messages for session: {}", sessionId);
        // Note: This would need to be implemented in DebateSessionService
        // For now, return a placeholder
        return Map.of("sessionId", sessionId, "messages", java.util.List.of());
    }

    /**
     * Get current scores
     * GET /api/debates/{sessionId}/scores
     */
    @GetMapping("/{sessionId}/scores")
    public Map<String, Object> getCurrentScores(@PathVariable Long sessionId) {
        return debateSessionService.getCurrentScores(sessionId);
    }

    /**
     * Complete debate session
     * POST /api/debates/{sessionId}/complete
     */
    @PostMapping("/{sessionId}/complete")
    public Map<String, Object> completeSession(@PathVariable Long sessionId) {
        log.info("Completing debate session: {}", sessionId);
        return debateSessionService.completeSession(sessionId);
    }

    /**
     * Submit user argument with streaming (SSE) - DEPRECATED
     * This endpoint is no longer supported in AI vs AI mode
     * POST /api/debates/{sessionId}/submit-argument-stream
     * Body: {argumentText, roundNumber, language}
     */
    @Deprecated
    @PostMapping(value = "/{sessionId}/submit-argument-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter submitArgumentStream(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request) {
        
        log.warn("Deprecated endpoint called: submit-argument-stream for session: {}", sessionId);
        
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        
        new Thread(() -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of(
                        "error", "This endpoint is deprecated in AI vs AI mode",
                        "message", "User arguments are no longer supported. Use automated debate flow instead."
                    )));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        
        return emitter;
    }

    /**
     * Stream automated AI vs AI debate
     * GET /api/debates/{sessionId}/stream-debate
     * Query params: language (optional, default: "en")
     */
    @GetMapping(value = "/{sessionId}/stream-debate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDebate(
            @PathVariable Long sessionId,
            @RequestParam(required = false, defaultValue = "en") String language) {
        
        log.info("Starting automated debate stream for session: {}, language: {}", sessionId, language);
        
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10 minutes timeout
        
        // Handle completion and errors
        emitter.onCompletion(() -> log.info("Debate stream completed for session: {}", sessionId));
        emitter.onTimeout(() -> log.warn("Debate stream timeout for session: {}", sessionId));
        emitter.onError(e -> log.error("Debate stream error for session: {}", sessionId, e));
        
        // Start streaming in a separate thread
        new Thread(() -> {
            try {
                debateOrchestrationService.streamAutomatedDebate(sessionId, language, emitter);
            } catch (Exception e) {
                log.error("Error during automated debate streaming", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                            "error", "Streaming failed",
                            "message", e.getMessage()
                        )));
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("Error sending error event", ex);
                }
            }
        }).start();
        
        return emitter;
    }

    /**
     * Pause automated debate
     * POST /api/debates/{sessionId}/pause
     */
    @PostMapping("/{sessionId}/pause")
    public Map<String, Object> pauseDebate(@PathVariable Long sessionId) {
        log.info("Pausing debate session: {}", sessionId);
        return debateOrchestrationService.pauseDebate(sessionId);
    }

    /**
     * Resume automated debate
     * POST /api/debates/{sessionId}/resume
     */
    @PostMapping("/{sessionId}/resume")
    public Map<String, Object> resumeDebate(@PathVariable Long sessionId) {
        log.info("Resuming debate session: {}", sessionId);
        return debateOrchestrationService.resumeDebate(sessionId);
    }

    /**
     * Skip to end (judging phase)
     * POST /api/debates/{sessionId}/skip-to-end
     */
    @PostMapping("/{sessionId}/skip-to-end")
    public Map<String, Object> skipToEnd(@PathVariable Long sessionId) {
        log.info("Skipping to end for debate session: {}", sessionId);
        return debateOrchestrationService.skipToEnd(sessionId);
    }
}
