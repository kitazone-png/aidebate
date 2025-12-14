package com.aidebate.adapter.web.controller;

import com.aidebate.app.service.DebateSessionService;
import com.aidebate.app.service.ModeratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streaming Controller
 * Handles SSE (Server-Sent Events) for real-time debate updates
 *
 * @author AI Debate Team
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StreamingController {

    private final ModeratorService moderatorService;
    private final DebateSessionService debateSessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Store active SSE connections
    private final Map<String, SseEmitter> moderatorEmitters = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> argumentEmitters = new ConcurrentHashMap<>();

    /**
     * Stream moderator messages for a debate session
     * GET /api/stream/moderator/{sessionId}?language=en
     */
    @GetMapping(value = "/moderator/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamModeratorMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "en") String language) {
        
        log.info("Starting moderator stream for session: {}, language: {}", sessionId, language);
        
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 minutes timeout
        String key = sessionId + "_" + language;
        
        emitter.onCompletion(() -> {
            log.info("Moderator stream completed for session: {}", sessionId);
            moderatorEmitters.remove(key);
        });
        
        emitter.onTimeout(() -> {
            log.warn("Moderator stream timeout for session: {}", sessionId);
            moderatorEmitters.remove(key);
        });
        
        emitter.onError(e -> {
            log.error("Moderator stream error for session: {}", sessionId, e);
            moderatorEmitters.remove(key);
        });
        
        moderatorEmitters.put(key, emitter);
        
        // Send initial welcome message
        try {
            Map<String, Object> welcomeMessage = moderatorService.generateWelcomeMessage(sessionId, language);
            sendModeratorMessage(key, welcomeMessage);
        } catch (Exception e) {
            log.error("Error sending welcome message", e);
        }
        
        return emitter;
    }

    /**
     * Stream AI argument generation
     * GET /api/stream/argument/{sessionId}
     */
    @GetMapping(value = "/argument/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamArgumentGeneration(@PathVariable Long sessionId) {
        log.info("Starting argument stream for session: {}", sessionId);
        
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 minutes timeout
        String key = String.valueOf(sessionId);
        
        emitter.onCompletion(() -> {
            log.info("Argument stream completed for session: {}", sessionId);
            argumentEmitters.remove(key);
        });
        
        emitter.onTimeout(() -> {
            log.warn("Argument stream timeout for session: {}", sessionId);
            argumentEmitters.remove(key);
        });
        
        emitter.onError(e -> {
            log.error("Argument stream error for session: {}", sessionId, e);
            argumentEmitters.remove(key);
        });
        
        argumentEmitters.put(key, emitter);
        
        return emitter;
    }

    /**
     * Send moderator message to active stream
     */
    public void sendModeratorMessage(Long sessionId, String language, Map<String, Object> message) {
        String key = sessionId + "_" + language;
        sendModeratorMessage(key, message);
    }

    private void sendModeratorMessage(String key, Map<String, Object> message) {
        SseEmitter emitter = moderatorEmitters.get(key);
        if (emitter != null) {
            try {
                String content = message.get("content").toString();
                // Stream character by character for typing effect
                for (int i = 0; i < content.length(); i++) {
                    String chunk = content.substring(0, i + 1);
                    Map<String, Object> chunkData = Map.of(
                        "type", message.get("type"),
                        "chunk", chunk,
                        "complete", i == content.length() - 1,
                        "timestamp", message.get("timestamp")
                    );
                    emitter.send(SseEmitter.event()
                        .name("moderator")
                        .data(objectMapper.writeValueAsString(chunkData)));
                    
                    // Small delay for typing effect
                    Thread.sleep(20);
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error sending moderator message", e);
                emitter.completeWithError(e);
                moderatorEmitters.remove(key);
            }
        }
    }

    /**
     * Send AI argument chunk to active stream
     */
    public void sendArgumentChunk(Long sessionId, String chunk, boolean complete, Long argumentId) {
        String key = String.valueOf(sessionId);
        SseEmitter emitter = argumentEmitters.get(key);
        if (emitter != null) {
            try {
                Map<String, Object> data = Map.of(
                    "chunk", chunk,
                    "complete", complete,
                    "argumentId", argumentId != null ? argumentId : 0
                );
                emitter.send(SseEmitter.event()
                    .name("ai_argument")
                    .data(objectMapper.writeValueAsString(data)));
                
                if (complete) {
                    emitter.complete();
                    argumentEmitters.remove(key);
                }
            } catch (IOException e) {
                log.error("Error sending argument chunk", e);
                emitter.completeWithError(e);
                argumentEmitters.remove(key);
            }
        }
    }

    /**
     * Get session state
     * GET /api/stream/state/{sessionId}
     */
    @GetMapping("/state/{sessionId}")
    public Map<String, Object> getSessionState(@PathVariable Long sessionId) {
        return debateSessionService.getSessionState(sessionId);
    }
}
