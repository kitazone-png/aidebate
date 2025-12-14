# TTS Audio Format Fix - Browser Compatibility

## Issue
**Error:** `NotSupportedError: Failed to load because no supported source was found`

**Root Cause:** The TTS service was generating PCM (raw audio) format, which browsers cannot play directly through the HTML5 `<audio>` element.

## Solution
Convert PCM audio to WAV format by adding a RIFF/WAV header. WAV is PCM with metadata that browsers understand.

## Changes Made

### 1. Added PCM to WAV Converter Method ✅
**File:** `aidebate-app/src/main/java/com/aidebate/app/service/VoiceAIService.java`

Added `convertPcmToWav()` method that:
- Takes raw PCM audio data (16-bit, 24kHz, mono)
- Adds 44-byte WAV/RIFF header
- Returns browser-compatible WAV audio

**Implementation:**
```java
private byte[] convertPcmToWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
    // Creates proper WAV header with:
    // - RIFF chunk descriptor
    // - fmt subchunk (audio format info)
    // - data subchunk (actual PCM data)
    // Returns complete WAV file
}
```

### 2. Updated generateSpeech() to Return WAV ✅
**File:** `aidebate-app/src/main/java/com/aidebate/app/service/VoiceAIService.java`

**Before:**
```java
byte[] audioData = audioStream.toByteArray();
return audioData;  // Returns raw PCM
```

**After:**
```java
byte[] audioData = audioStream.toByteArray();
byte[] wavAudio = convertPcmToWav(audioData, 24000, 1, 16);
return wavAudio;  // Returns WAV format
```

### 3. Updated Controller Content-Type ✅
**File:** `aidebate-adapter/src/main/java/com/aidebate/adapter/web/controller/VoiceController.java`

**Before:**
```java
headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
```

**After:**
```java
headers.setContentType(MediaType.parseMediaType("audio/wav"));
```

### 4. Fixed Default Voice IDs ✅
Also corrected fallback voice IDs to use valid Qwen TTS voices:
- Chinese: `longyuan` (was `xiaoyun`)
- English: `longyuan_en` (was `xiaogang`)

## Technical Details

### Audio Format Configuration
- **Input from TTS:** PCM, 24kHz, Mono, 16-bit
- **Output to Browser:** WAV, 24kHz, Mono, 16-bit

### WAV Header Structure
```
Bytes 0-3:   "RIFF" - Chunk ID
Bytes 4-7:   File size - 8
Bytes 8-11:  "WAVE" - Format
Bytes 12-15: "fmt " - Subchunk1 ID
Bytes 16-19: 16 - Subchunk1 size (PCM)
Bytes 20-21: 1 - Audio format (PCM)
Bytes 22-23: 1 - Number of channels (Mono)
Bytes 24-27: 24000 - Sample rate
Bytes 28-31: Byte rate (sampleRate * channels * bitsPerSample / 8)
Bytes 32-33: Block align (channels * bitsPerSample / 8)
Bytes 34-35: 16 - Bits per sample
Bytes 36-39: "data" - Subchunk2 ID
Bytes 40-43: PCM data size
Bytes 44+:   Actual PCM audio data
```

## Browser Compatibility

### Supported Audio Formats in HTML5
| Format | Chrome | Firefox | Safari | Edge |
|--------|--------|---------|--------|------|
| MP3    | ✅     | ✅      | ✅     | ✅   |
| WAV    | ✅     | ✅      | ✅     | ✅   |
| OGG    | ✅     | ✅      | ❌     | ✅   |
| AAC    | ✅     | Limited | ✅     | ✅   |
| PCM    | ❌     | ❌      | ❌     | ❌   |

**WAV** is universally supported and perfect for our use case.

## Testing Steps

1. **Restart Application:**
   ```bash
   cd c:\workbench\coding\aicoding\aidebate\aidebate-start
   mvn spring-boot:run
   ```

2. **Test Audio Playback:**
   - Start a debate session
   - Click the 🔊 button on any message
   - Audio should play successfully

3. **Verify in Browser:**
   - Open Developer Tools (F12)
   - Network tab should show:
     - Request to `/api/voice/generate-speech`
     - Response type: `audio/wav`
     - Status: 200 OK
   - No console errors

4. **Check Audio Quality:**
   - Listen to Affirmative (male voice)
   - Listen to Negative (female voice)
   - Verify voices are distinct
   - Test both Chinese and English

## Why This Works

### Problem with PCM
PCM is raw audio data without any file format structure:
- No header describing sample rate, channels, bit depth
- Browsers don't know how to interpret it
- Cannot be played by HTML5 `<audio>` element

### Solution with WAV
WAV adds a standardized header to PCM:
- Tells browser: sample rate, channels, bit depth
- Universally supported format
- No transcoding needed (just add header)
- Minimal overhead (44 bytes)

## Performance Impact

- **Processing Time:** Negligible (~1ms to add header)
- **File Size:** Only 44 bytes larger than PCM
- **Quality:** Identical to original PCM (lossless)
- **Memory:** Minimal (one extra array allocation)

## Alternative Approaches Considered

### 1. MP3 Conversion ❌
**Pros:** Better compression, smaller file size
**Cons:** 
- Requires encoding library (complex)
- Lossy compression
- Higher CPU usage
- Not supported by Qwen TTS API format enum

### 2. Using Native MP3 from TTS ❌
**Issue:** The SDK's `QwenTtsRealtimeAudioFormat` enum doesn't have an MP3 option available in version 2.21.16

### 3. WAV with Header (Chosen) ✅
**Pros:**
- Simple implementation
- No external libraries
- Lossless quality
- Universal browser support
- Fast processing

## Next Steps

**✅ Complete** - Audio playback should now work in all browsers!

If you still encounter issues:
1. Check browser console for errors
2. Verify network response content-type is `audio/wav`
3. Test with different browsers
4. Check server logs for any TTS generation errors

## Files Modified

1. `aidebate-app/src/main/java/com/aidebate/app/service/VoiceAIService.java`
   - Added `convertPcmToWav()` method
   - Modified `generateSpeech()` to convert PCM to WAV
   - Fixed default fallback voice IDs

2. `aidebate-adapter/src/main/java/com/aidebate/adapter/web/controller/VoiceController.java`
   - Changed response content-type from `audio/mpeg` to `audio/wav`

## Verification

Build status: ✅ **SUCCESS**
```
[INFO] BUILD SUCCESS
[INFO] Total time:  9.372 s
```

All modules compiled successfully. Ready for testing!
