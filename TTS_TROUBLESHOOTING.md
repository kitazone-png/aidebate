# TTS Troubleshooting Guide

## Current Issue: HTTP 500 Error

### Error Observed
```
Error playing audio: Error: HTTP error! status: 500
    at playMessageAudio (app-chat.js:1293:19)
```

## Recent Fixes Applied

### 1. Enhanced Error Logging ✅
Updated `VoiceController.java` to return detailed error messages in JSON format instead of generic 500 errors.

**Change:**
```java
// Before: Generic error
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

// After: Detailed error with message
String errorResponse = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", 
        e.getClass().getSimpleName(), 
        e.getMessage());
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .headers(headers)
        .body(errorResponse.getBytes());
```

### 2. Voice ID Configuration ✅
Ensured all voice IDs use only valid Qwen TTS voice names:

**Valid Voice IDs:**
- Chinese: `longwan`, `longxiaochun`, `longxiaobai`, `longyuan`
- English: `longwan_en`, `longxiaobai_en`, `longyuan_en`

## Potential Root Causes

### 1. **API Key Issue** (Most Likely)
**Check:** Is the API key valid for Alibaba DashScope?

Current configuration in `application.yml`:
```yaml
spring:
  ai:
    alibaba:
      api-key: sk-df320765144942a298260c24af4ff2d9
```

**Important:** The API key format `sk-...` looks like an OpenAI key format, not Alibaba DashScope format.

**Alibaba DashScope API keys typically:**
- Start with different prefixes
- Are longer (usually 40+ characters)
- Can be obtained from: https://dashscope.console.aliyun.com/apiKey

**Action Required:**
1. Get a valid Alibaba DashScope API key
2. Update `application.yml` with the correct key
3. Restart the application

### 2. **Network/Firewall Issue**
The TTS service connects to:
```
wss://dashscope.aliyuncs.com/api-ws/v1/realtime
```

**Check:**
- Can your server reach this WebSocket endpoint?
- Are there any firewall rules blocking WebSocket connections?
- Is there a proxy that needs configuration?

### 3. **Voice ID Still Invalid**
Even though we've updated the code, ensure the compiled version is running.

**Verify:**
```bash
# Check if rebuild succeeded
mvn clean compile -DskipTests

# Restart application to load new compiled code
```

### 4. **Rate Limiting**
Alibaba Cloud may have rate limits on the free tier.

**Symptoms:**
- Works for first few requests
- Starts failing after multiple attempts
- Error message mentions quota or rate limit

## Debugging Steps

### Step 1: Check Server Logs
After restarting the application and trying to play audio, check the logs for detailed error:

```bash
# Look in console output or log file
tail -f logs/aidebate.log

# Or check last 100 lines
tail -100 logs/aidebate.log | grep -i "error\|tts\|voice"
```

Look for lines like:
```
ERROR c.aidebate.adapter.web.controller.VoiceController - Error generating speech - Message: ...
ERROR c.aidebate.app.service.VoiceAIService - TTS generation failed: ...
```

### Step 2: Test Service Availability
Call the status endpoint to verify configuration:

```bash
curl http://localhost:8081/api/voice/status
```

Expected response:
```json
{
  "available": true,
  "supportedRoles": ["AFFIRMATIVE", "NEGATIVE", "MODERATOR", "ORGANIZER", "JUDGE"],
  "supportedLanguages": ["zh", "en"]
}
```

If `available` is `false`, the API key is not configured.

### Step 3: Test with Simple Request
Try generating audio with a simple test:

```bash
curl -X POST http://localhost:8081/api/voice/generate-speech \
  -H "Content-Type: application/json" \
  -d '{"text":"测试", "role":"MODERATOR", "language":"zh"}' \
  --output test-audio.pcm
```

**Success:** You'll get a binary audio file
**Failure:** You'll get a JSON error message with details

### Step 4: Verify Voice IDs in Running Code
Check logs when you try to play audio. You should see:
```
INFO c.aidebate.app.service.VoiceAIService - Using voice profile: longxiaochun for role: AFFIRMATIVE
```

If you see different voice IDs (like `xiaoyun` or `zhixiaobai`), the old code is still running.

## Solution Checklist

- [ ] **Restart Application** - Must restart after code changes
  ```bash
  # Stop current application (Ctrl+C)
  cd c:\workbench\coding\aicoding\aidebate\aidebate-start
  mvn spring-boot:run
  ```

- [ ] **Verify API Key** - Ensure it's a valid Alibaba DashScope key
  - Get key from: https://dashscope.console.aliyun.com/apiKey
  - Update in `aidebate-start/src/main/resources/application.yml`
  - Format should be a long alphanumeric string

- [ ] **Check Network Connectivity**
  ```bash
  # Test if you can reach DashScope
  curl -I https://dashscope.aliyuncs.com
  ```

- [ ] **Review Server Logs** - Look for specific error messages

- [ ] **Test Status Endpoint** - Verify service is available

## Common Error Messages and Solutions

### "Invalid API key" or "Authentication failed"
**Cause:** API key is incorrect or expired  
**Solution:** Get new API key from Alibaba Cloud console

### "Invalid voice, SetVoice failed"
**Cause:** Using wrong voice ID  
**Solution:** Ensure voice IDs are: longwan, longxiaochun, longxiaobai, longyuan (+ _en for English)

### "TTS generation timeout"
**Cause:** Network issue or server overload  
**Solution:** Check network connectivity, try again

### "No audio data generated"
**Cause:** Text is empty or service returned empty response  
**Solution:** Verify request text is not empty, check API quota

## Next Steps

1. **Restart the application** with the updated code
2. **Check the browser console** for the actual error response (it will now include error details)
3. **Check server logs** for detailed error messages
4. **Share the specific error message** from either browser or logs

The enhanced error logging will now show you exactly what's failing, making it much easier to diagnose and fix the issue.

## Getting a Valid API Key

If you don't have a valid Alibaba DashScope API key:

1. Visit: https://dashscope.console.aliyun.com/
2. Sign up or log in
3. Go to API Keys section
4. Create a new API key
5. Copy the key (it will be a long string, not starting with 'sk-')
6. Update `application.yml`:
   ```yaml
   spring:
     ai:
       alibaba:
         api-key: YOUR_ACTUAL_DASHSCOPE_KEY_HERE
   ```
7. Restart the application

The TTS feature requires a valid DashScope API key to work!
