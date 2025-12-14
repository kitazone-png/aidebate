package com.aidebate.adapter.web.controller;

import com.aidebate.app.service.VoiceAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/**
 * Voice Controller
 * Handles text-to-speech API requests
 * 
 * @author AI Debate Team
 */
@Slf4j
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VoiceController {

    private final VoiceAIService voiceAIService;

    /**
     * Generate speech from text with streaming response
     * 
     * POST /api/voice/generate-speech
     * 
     * Request body:
     * {
     *   "text": "Content to read aloud",
     *   "role": "AFFIRMATIVE",
     *   "language": "zh"
     * }
     * 
     * @param request Request containing text, role, and language
     * @return Streaming audio data as WAV
     */
    @PostMapping("/generate-speech")
    public ResponseEntity<StreamingResponseBody> generateSpeech(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            String role = request.get("role");
            String language = request.get("language");

            // Validate input
            if (text == null || text.trim().isEmpty()) {
                log.warn("Empty text provided for speech generation");
                return ResponseEntity.badRequest().build();
            }

            if (role == null || role.trim().isEmpty()) {
                log.warn("Role not specified, using default");
                role = "MODERATOR";
            }

            if (language == null || language.trim().isEmpty()) {
                log.warn("Language not specified, using default");
                language = "zh";
            }

            // Check if service is available
            if (!voiceAIService.isServiceAvailable()) {
                log.error("Voice service is not available");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            log.info("Generating speech - Role: {}, Language: {}, Text length: {}", 
                    role, language, text.length());

            // Capture parameters for lambda
            final String finalRole = role;
            final String finalLanguage = language;

            // Create streaming response
            StreamingResponseBody responseBody = outputStream -> {
                try {
                    // Use streaming generation from service
                    voiceAIService.generateSpeechStream(text, finalRole, finalLanguage, (chunk, isComplete) -> {
                        if (chunk.length > 0) {
                            outputStream.write(chunk);
                            outputStream.flush();
                        }
                        if (isComplete) {
                            log.info("Audio streaming completed");
                        }
                    });
                    
                } catch (Exception e) {
                    log.error("Error during audio streaming", e);
                    throw new RuntimeException("Failed to stream audio: " + e.getMessage(), e);
                }
            };

            // Prepare response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/wav"));
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);
            // Note: Content-Length is not set for streaming responses

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(responseBody);

        } catch (Exception e) {
            log.error("Error generating speech - Message: {}, Cause: {}", e.getMessage(), e.getCause(), e);
            // Return error details in response for debugging
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String errorResponse = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", 
                    e.getClass().getSimpleName(), 
                    e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Unknown error");
            
            StreamingResponseBody errorBody = outputStream -> {
                outputStream.write(errorResponse.getBytes());
                outputStream.flush();
            };
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(headers)
                    .body(errorBody);
        }
    }

    /**
     * Get voice service status
     * 
     * GET /api/voice/status
     * 
     * @return Service status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getServiceStatus() {
        boolean available = voiceAIService.isServiceAvailable();
        
        return ResponseEntity.ok(Map.of(
                "available", available,
                "supportedRoles", voiceAIService.getSupportedRoles(),
                "supportedLanguages", voiceAIService.getSupportedLanguages()
        ));
    }
}
