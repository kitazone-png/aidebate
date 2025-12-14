package com.aidebate.app.service;

import com.alibaba.dashscope.audio.qwen_tts_realtime.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Voice AI Service
 * Manages text-to-speech operations using Alibaba Cloud TTS
 * Provides role-based voice differentiation for debate participants
 * 
 * Uses Alibaba DashScope Speech Synthesis API for real-time audio generation
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceAIService {

    @Value("${spring.ai.alibaba.api-key}")
    private String apiKey;

    // Voice configuration maps for different roles and languages
    private static final Map<String, String> VOICE_PROFILES_ZH = new HashMap<>();
    private static final Map<String, String> VOICE_PROFILES_EN = new HashMap<>();

    static {
        // Chinese voices - using valid Qwen TTS voice IDs
        // Available voices: longwan (female), longxiaochun (male), longxiaobai (male), longyuan (male)
        VOICE_PROFILES_ZH.put("AFFIRMATIVE", "Cherry");    // 龙小春 - clear, confident male voice
        VOICE_PROFILES_ZH.put("NEGATIVE", "Cherry");             // 龙婉 - professional female voice
        VOICE_PROFILES_ZH.put("MODERATOR", "Cherry");           // 龙渊 - neutral, authoritative male
        VOICE_PROFILES_ZH.put("ORGANIZER", "Cherry");           // 龙渊 - formal voice
        VOICE_PROFILES_ZH.put("JUDGE", "Cherry");            // 龙小白 - experienced tone

        // English voices - using valid Qwen TTS voice IDs
        // Available voices: longxiaobai_en (male), longyuan_en (male), longwan_en (female)
        VOICE_PROFILES_EN.put("AFFIRMATIVE", "Cherry");   // Male, clear
        VOICE_PROFILES_EN.put("NEGATIVE", "Cherry");          // Female, professional
        VOICE_PROFILES_EN.put("MODERATOR", "Cherry");        // Neutral, authoritative
        VOICE_PROFILES_EN.put("ORGANIZER", "Cherry");        // Formal
        VOICE_PROFILES_EN.put("JUDGE", "Cherry");         // Experienced
    }

    /**
     * Generate speech from text with role-specific voice using Alibaba Cloud TTS
     * Uses Qwen realtime TTS API to generate PCM audio, then converts to WAV
     * 
     * @param text     Text content to convert to speech
     * @param role     Speaker role (AFFIRMATIVE, NEGATIVE, MODERATOR, etc.)
     * @param language Language code (zh or en)
     * @return Audio data as byte array (WAV format)
     */
    public byte[] generateSpeech(String text, String role, String language) {
        log.info("Generating speech for role: {}, language: {}, text length: {}", role, language, text.length());

        QwenTtsRealtime qwenTtsRealtime = null;
        final boolean[] connectionClosed = {false};
        
        try {
            // Get voice profile for role and language
            String voiceId = getVoiceProfile(role, language);
            log.info("Using voice profile: {} for role: {}", voiceId, role);

            // Prepare to collect audio data
            ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
            CountDownLatch completeLatch = new CountDownLatch(1);
            final boolean[] hasError = {false};
            final String[] errorMessage = {null};
            
            // Configure TTS parameters
            QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                    .model("qwen3-tts-flash-realtime")
                    .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                    .apikey(apiKey)
                    .build();

            // Create TTS client with callback
            qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
                @Override
                public void onOpen() {
                    log.debug("TTS connection opened");
                }

                @Override
                public void onEvent(JsonObject message) {
                    try {
                        String type = message.get("type").getAsString();
                        log.debug("Received TTS event: {}", type);
                        
                        switch(type) {
                            case "response.audio.delta":
                                // Decode and collect audio data
                                String recvAudioB64 = message.get("delta").getAsString();
                                byte[] audioChunk = Base64.getDecoder().decode(recvAudioB64);
                                synchronized (audioStream) {
                                    audioStream.write(audioChunk);
                                }
                                log.debug("Received audio chunk: {} bytes", audioChunk.length);
                                break;
                            case "response.done":
                                log.debug("Response done, audio generation complete");
                                break;
                            case "session.finished":
                                // Audio generation complete
                                log.info("Session finished, total audio collected: {} bytes", audioStream.size());
                                completeLatch.countDown();
                                break;
                            case "error":
                                String error = message.has("error") ? message.get("error").toString() : "Unknown error";
                                log.error("TTS error event: {}", error);
                                hasError[0] = true;
                                errorMessage[0] = error;
                                completeLatch.countDown();
                                break;
                            default:
                                break;
                        }
                    } catch (Exception e) {
                        log.error("Error processing TTS event", e);
                        hasError[0] = true;
                        errorMessage[0] = e.getMessage();
                        completeLatch.countDown();
                    }
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("TTS connection closed by server: {} - {}", code, reason);
                    connectionClosed[0] = true;
                    // Ensure completion latch is released even if session.finished not received
                    completeLatch.countDown();
                }
            });

            // Connect and configure session
            log.debug("Connecting to TTS service...");
            qwenTtsRealtime.connect();
            
            // Give connection time to establish
            Thread.sleep(500);
            
            QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                    .voice(voiceId)
                    .responseFormat(QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT)
                    .mode("server_commit")
                    .build();
            log.debug("Updating TTS session configuration...");
            qwenTtsRealtime.updateSession(config);

            // Send text for synthesis
            log.debug("Sending text for synthesis: {} characters", text.length());
            qwenTtsRealtime.appendText(text);
            qwenTtsRealtime.finish();

            // Wait for completion (timeout after 30 seconds)
            log.debug("Waiting for audio generation to complete...");
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            
            if (!completed) {
                log.error("TTS generation timeout after 30 seconds");
                throw new RuntimeException("TTS generation timeout");
            }
            
            if (hasError[0]) {
                log.error("TTS generation failed: {}", errorMessage[0]);
                throw new RuntimeException("TTS generation error: " + errorMessage[0]);
            }

            byte[] audioData = audioStream.toByteArray();
            
            if (audioData.length == 0) {
                log.error("No audio data received from TTS service");
                throw new RuntimeException("No audio data generated");
            }
            
            // Convert PCM to WAV for browser compatibility
            byte[] wavAudio = convertPcmToWav(audioData, 24000, 1, 16);
            
            log.info("Speech generated successfully, PCM size: {} bytes, WAV size: {} bytes", 
                    audioData.length, wavAudio.length);
            return wavAudio;

        } catch (NoApiKeyException e) {
            log.error("API key not configured for TTS service", e);
            throw new RuntimeException("Voice service configuration error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error("TTS generation interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("TTS generation interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error generating speech", e);
            throw new RuntimeException("Failed to generate speech: " + e.getMessage(), e);
        } finally {
            // Close connection in finally block to ensure cleanup
            // Only close if not already closed by server
            if (qwenTtsRealtime != null && !connectionClosed[0]) {
                try {
                    log.debug("Closing TTS connection...");
                    qwenTtsRealtime.close();
                    log.debug("TTS connection closed successfully");
                } catch (Exception e) {
                    // Ignore errors if connection is already closed
                    if (!e.getMessage().contains("already closed")) {
                        log.warn("Error closing TTS connection: {}", e.getMessage());
                    }
                }
            } else if (connectionClosed[0]) {
                log.debug("TTS connection already closed by server, skipping manual close");
            }
        }
    }

    /**
     * Callback interface for streaming audio generation
     */
    public interface AudioStreamCallback {
        /**
         * Called when audio chunk is received
         * @param chunk Audio data chunk
         * @param isComplete Whether this is the final chunk
         */
        void onChunk(byte[] chunk, boolean isComplete) throws Exception;
    }

    /**
     * Generate speech with streaming callback
     * Collects all audio first, then sends as complete WAV file for browser compatibility
     * 
     * @param text     Text content to convert to speech
     * @param role     Speaker role
     * @param language Language code
     * @param callback Callback to receive audio chunks
     */
    public void generateSpeechStream(String text, String role, String language, AudioStreamCallback callback) {
        log.info("Generating streaming speech for role: {}, language: {}, text length: {}", role, language, text.length());

        QwenTtsRealtime qwenTtsRealtime = null;
        final boolean[] connectionClosed = {false};
        
        try {
            // Get voice profile for role and language
            String voiceId = getVoiceProfile(role, language);
            log.info("Using voice profile: {} for role: {}", voiceId, role);

            // Collect all PCM audio before converting to WAV (needed for correct header)
            ByteArrayOutputStream pcmAccumulator = new ByteArrayOutputStream();
            CountDownLatch completeLatch = new CountDownLatch(1);
            final boolean[] hasError = {false};
            final String[] errorMessage = {null};
            
            // Configure TTS parameters
            QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                    .model("qwen3-tts-flash-realtime")
                    .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                    .apikey(apiKey)
                    .build();

            // Create TTS client with callback
            qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
                @Override
                public void onOpen() {
                    log.debug("TTS streaming connection opened");
                }

                @Override
                public void onEvent(JsonObject message) {
                    try {
                        String type = message.get("type").getAsString();
                        log.debug("Received TTS streaming event: {}", type);
                        
                        switch(type) {
                            case "response.audio.delta":
                                // Decode and accumulate audio chunk
                                String recvAudioB64 = message.get("delta").getAsString();
                                byte[] pcmChunk = Base64.getDecoder().decode(recvAudioB64);
                                
                                synchronized (pcmAccumulator) {
                                    pcmAccumulator.write(pcmChunk);
                                    log.debug("Accumulated PCM chunk: {} bytes (total: {} bytes)", 
                                            pcmChunk.length, pcmAccumulator.size());
                                }
                                break;
                                
                            case "response.done":
                                log.debug("Response done, audio generation complete");
                                break;
                                
                            case "session.finished":
                                // Convert accumulated PCM to WAV and send as one complete file
                                byte[] pcmData = pcmAccumulator.toByteArray();
                                log.info("Session finished, converting {} bytes PCM to WAV", pcmData.length);
                                
                                if (pcmData.length > 0) {
                                    byte[] wavAudio = convertPcmToWav(pcmData, 24000, 1, 16);
                                    log.info("Sending complete WAV file: {} bytes", wavAudio.length);
                                    callback.onChunk(wavAudio, true);
                                } else {
                                    log.warn("No audio data generated");
                                    callback.onChunk(new byte[0], true);
                                }
                                completeLatch.countDown();
                                break;
                                
                            case "error":
                                String error = message.has("error") ? message.get("error").toString() : "Unknown error";
                                log.error("TTS streaming error event: {}", error);
                                hasError[0] = true;
                                errorMessage[0] = error;
                                completeLatch.countDown();
                                break;
                                
                            default:
                                break;
                        }
                    } catch (Exception e) {
                        log.error("Error processing TTS streaming event", e);
                        hasError[0] = true;
                        errorMessage[0] = e.getMessage();
                        completeLatch.countDown();
                    }
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("TTS streaming connection closed by server: {} - {}", code, reason);
                    connectionClosed[0] = true;
                    completeLatch.countDown();
                }
            });

            // Connect and configure session
            log.debug("Connecting to TTS streaming service...");
            qwenTtsRealtime.connect();
            
            // Give connection time to establish
            Thread.sleep(500);
            
            QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                    .voice(voiceId)
                    .responseFormat(QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT)
                    .mode("server_commit")
                    .build();
            log.debug("Updating TTS streaming session configuration...");
            qwenTtsRealtime.updateSession(config);

            // Send text for synthesis
            log.debug("Sending text for streaming synthesis: {} characters", text.length());
            qwenTtsRealtime.appendText(text);
            qwenTtsRealtime.finish();

            // Wait for completion (timeout after 30 seconds)
            log.debug("Waiting for streaming audio generation to complete...");
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            
            if (!completed) {
                log.error("TTS streaming generation timeout after 30 seconds");
                throw new RuntimeException("TTS streaming generation timeout");
            }
            
            if (hasError[0]) {
                log.error("TTS streaming generation failed: {}", errorMessage[0]);
                throw new RuntimeException("TTS streaming generation error: " + errorMessage[0]);
            }

            log.info("Speech streaming completed successfully");

        } catch (NoApiKeyException e) {
            log.error("API key not configured for TTS streaming service", e);
            throw new RuntimeException("Voice service configuration error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error("TTS streaming generation interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("TTS streaming generation interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error generating streaming speech", e);
            throw new RuntimeException("Failed to generate streaming speech: " + e.getMessage(), e);
        } finally {
            // Close connection in finally block to ensure cleanup
            if (qwenTtsRealtime != null && !connectionClosed[0]) {
                try {
                    log.debug("Closing TTS streaming connection...");
                    qwenTtsRealtime.close();
                    log.debug("TTS streaming connection closed successfully");
                } catch (Exception e) {
                    if (!e.getMessage().contains("already closed")) {
                        log.warn("Error closing TTS streaming connection: {}", e.getMessage());
                    }
                }
            } else if (connectionClosed[0]) {
                log.debug("TTS streaming connection already closed by server, skipping manual close");
            }
        }
    }



    /**
     * Get voice profile ID for a specific role and language
     * 
     * @param role     Speaker role
     * @param language Language code
     * @return Voice ID for the TTS service
     */
    private String getVoiceProfile(String role, String language) {
        Map<String, String> voiceMap = "zh".equalsIgnoreCase(language) 
                ? VOICE_PROFILES_ZH 
                : VOICE_PROFILES_EN;
        
        String voiceId = voiceMap.get(role.toUpperCase());
        
        // Default fallback voices if role not found
        if (voiceId == null) {
            if ("zh".equalsIgnoreCase(language)) {
                voiceId = "longyuan"; // Default Chinese voice
            } else {
                voiceId = "longyuan_en";    // Default English voice
            }
            log.warn("Voice profile not found for role: {}, using default: {}", role, voiceId);
        }
        
        return voiceId;
    }

    /**
     * Validate if TTS service is available
     * 
     * @return true if service is configured and available
     */
    public boolean isServiceAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Get supported roles for voice profiles
     * 
     * @return Array of supported role names
     */
    public String[] getSupportedRoles() {
        return new String[]{"AFFIRMATIVE", "NEGATIVE", "MODERATOR", "ORGANIZER", "JUDGE"};
    }

    /**
     * Get supported languages
     * 
     * @return Array of supported language codes
     */
    public String[] getSupportedLanguages() {
        return new String[]{"zh", "en"};
    }

    /**
     * Convert PCM audio data to WAV format
     * WAV format is PCM with a RIFF header that browsers can play
     * 
     * @param pcmData Raw PCM audio data
     * @param sampleRate Sample rate (e.g., 24000)
     * @param channels Number of channels (1 for mono, 2 for stereo)
     * @param bitsPerSample Bits per sample (e.g., 16)
     * @return WAV formatted audio data
     */
    private byte[] convertPcmToWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        byte[] wavHeader = createWavHeader(pcmData.length, sampleRate, channels, bitsPerSample);
        byte[] wavData = new byte[wavHeader.length + pcmData.length];
        
        // Copy header and PCM data
        System.arraycopy(wavHeader, 0, wavData, 0, wavHeader.length);
        System.arraycopy(pcmData, 0, wavData, wavHeader.length, pcmData.length);
        
        return wavData;
    }

    /**
     * Create WAV header for PCM audio
     * 
     * @param pcmDataSize Size of PCM data (0 for streaming/unknown size)
     * @param sampleRate Sample rate
     * @param channels Number of channels
     * @param bitsPerSample Bits per sample
     * @return WAV header bytes (44 bytes)
     */
    private byte[] createWavHeader(int pcmDataSize, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        
        // WAV header is 44 bytes
        byte[] wavHeader = new byte[44];
        
        // RIFF header
        wavHeader[0] = 'R';
        wavHeader[1] = 'I';
        wavHeader[2] = 'F';
        wavHeader[3] = 'F';
        
        // File size (total size - 8 bytes for RIFF header)
        int fileSize = 36 + pcmDataSize;
        wavHeader[4] = (byte) (fileSize & 0xff);
        wavHeader[5] = (byte) ((fileSize >> 8) & 0xff);
        wavHeader[6] = (byte) ((fileSize >> 16) & 0xff);
        wavHeader[7] = (byte) ((fileSize >> 24) & 0xff);
        
        // WAVE header
        wavHeader[8] = 'W';
        wavHeader[9] = 'A';
        wavHeader[10] = 'V';
        wavHeader[11] = 'E';
        
        // fmt subchunk
        wavHeader[12] = 'f';
        wavHeader[13] = 'm';
        wavHeader[14] = 't';
        wavHeader[15] = ' ';
        
        // Subchunk1 size (16 for PCM)
        wavHeader[16] = 16;
        wavHeader[17] = 0;
        wavHeader[18] = 0;
        wavHeader[19] = 0;
        
        // Audio format (1 = PCM)
        wavHeader[20] = 1;
        wavHeader[21] = 0;
        
        // Number of channels
        wavHeader[22] = (byte) channels;
        wavHeader[23] = 0;
        
        // Sample rate
        wavHeader[24] = (byte) (sampleRate & 0xff);
        wavHeader[25] = (byte) ((sampleRate >> 8) & 0xff);
        wavHeader[26] = (byte) ((sampleRate >> 16) & 0xff);
        wavHeader[27] = (byte) ((sampleRate >> 24) & 0xff);
        
        // Byte rate
        wavHeader[28] = (byte) (byteRate & 0xff);
        wavHeader[29] = (byte) ((byteRate >> 8) & 0xff);
        wavHeader[30] = (byte) ((byteRate >> 16) & 0xff);
        wavHeader[31] = (byte) ((byteRate >> 24) & 0xff);
        
        // Block align
        wavHeader[32] = (byte) blockAlign;
        wavHeader[33] = 0;
        
        // Bits per sample
        wavHeader[34] = (byte) bitsPerSample;
        wavHeader[35] = 0;
        
        // data subchunk
        wavHeader[36] = 'd';
        wavHeader[37] = 'a';
        wavHeader[38] = 't';
        wavHeader[39] = 'a';
        
        // Subchunk2 size (actual audio data size)
        wavHeader[40] = (byte) (pcmDataSize & 0xff);
        wavHeader[41] = (byte) ((pcmDataSize >> 8) & 0xff);
        wavHeader[42] = (byte) ((pcmDataSize >> 16) & 0xff);
        wavHeader[43] = (byte) ((pcmDataSize >> 24) & 0xff);
        
        return wavHeader;
    }
}
