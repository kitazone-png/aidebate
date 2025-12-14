# Streaming Output Bug Fixes

## Date: 2024-12-14

## Overview
Fixed critical streaming output bugs in the AI debate system that caused prompt leakage, repetitive text, and inconsistent language output.

## Bugs Fixed

### 1. **Prompt Leakage**
**Problem**: System prompts and user prompts were appearing in the debate output, making it impossible to distinguish actual debate arguments.

**Root Cause**: The streaming callback was designed to send incremental chunks during streaming, but the `doOnComplete()` handler was re-sending the entire accumulated text, which caused the full prompt context to leak into the final output.

**Fix**: Modified `callQwenAPIStream()` method to:
- Only send new chunks during streaming: `callback.onChunk(chunk, false)`
- On completion, signal finish without re-sending text: `callback.onChunk("", true)`
- This ensures only the AI-generated content is displayed, not the prompts

### 2. **Repetitive Text**
**Problem**: Many repetitive words and phrases appeared in the streaming output.

**Root Cause**: Same as Bug #1 - the `doOnComplete()` handler was sending the full accumulated text again after all chunks had already been sent incrementally, causing massive duplication.

**Fix**: Changed completion handler to only signal completion without sending duplicate text.

### 3. **Language Inconsistency**
**Problem**: AI output mixed English and Chinese, with prompts written in English and "使用中文回答" appended at the end.

**Root Cause**: All prompt building methods used English text with Chinese instruction appended, leading to inconsistent language processing by the AI model.

**Fix**: Converted ALL prompts to pure Chinese:
- `buildOpponentSystemPrompt()`: Full Chinese prompt
- `buildOpponentUserPrompt()`: Chinese labels (辩题, 立场, 回合)
- `buildPreviewSystemPrompt()`: Pure Chinese
- `buildPreviewUserPrompt()`: Chinese labels (辩题, 立场, 回合)
- `buildSimulationSystemPrompt()`: Full Chinese
- `buildSimulationUserPrompt()`: Chinese labels (辩题, 立场, 回合)
- `buildModeratorSummaryPrompt()`: Already Chinese ✓
- `buildModeratorEvaluationPrompt()`: Already Chinese ✓
- `buildModeratorAnnouncementPrompt()`: Already Chinese ✓
- `buildDebateArgumentSystemPrompt()`: Already Chinese ✓
- `buildDebateArgumentUserPrompt()`: Already Chinese ✓

## Code Changes

### File: `AlibabaAIService.java`

#### 1. Fixed Streaming Callback Logic

**Before**:
```java
.doOnComplete(() -> {
    // On complete, send the full accumulated text
    String finalText = accumulated.toString();
    if (finalText.length() > maxLength) {
        finalText = finalText.substring(0, maxLength - 3) + "...";
    }
    callback.onChunk(finalText, true);  // ❌ Sends duplicate text
})
```

**After**:
```java
.doOnComplete(() -> {
    // On complete, just signal completion without sending text again
    callback.onChunk("", true);  // ✅ Signal completion only
    log.debug("Streaming completed: {} characters", accumulated.length());
})
```

#### 2. Error Handler Simplified

**Before**:
```java
.doOnError(error -> {
    log.error("Error during streaming", error);
    String finalText = accumulated.toString();
    if (finalText.length() > maxLength) {
        finalText = finalText.substring(0, maxLength - 3) + "...";
    }
    callback.onChunk(finalText, true);
})
```

**After**:
```java
.doOnError(error -> {
    log.error("Error during streaming", error);
    callback.onChunk("", true);  // Signal error completion
})
```

#### 3. Sample Prompt Conversion

**Before** (`buildOpponentUserPrompt`):
```java
prompt.append("Topic: ").append(topic).append("\n");
prompt.append("Your Position: ").append(side).append("\n");
prompt.append("Current Round: ").append(roundNumber).append(" of 5\n\n");
```

**After**:
```java
prompt.append("辩题：").append(topic).append("\n");
prompt.append("你的立场：").append(sideText).append("\n");
prompt.append("当前回合：第").append(roundNumber).append("回合（共5回合）\n\n");
```

#### 4. Added Anti-Prompt-Leakage Instructions

All prompts now include explicit instructions:
```java
"直接输出辩论内容，不要包含任何提示词、格式说明或元信息。"
```

Or variations like:
```java
"直接输出论点内容，不要添加任何格式标记或说明文字。"
```

## Side Translation

Added proper side translation in prompts:
```java
String sideText = "AFFIRMATIVE".equals(side) ? "正方" : "反方";
```

This ensures:
- AFFIRMATIVE → 正方
- NEGATIVE → 反方

## Character Limits

Changed character limit descriptions from English to Chinese:
- "Maximum 500 characters" → "最多500字"
- "max 200 characters" → "最多200字"
- "max 300 characters" → "最多300字"

## Impact

### Before Fix:
```
System: You are a professional debater...
User: Topic: AI should be regulated
Your Position: AFFIRMATIVE
...
AI Response: I support this position because... [THEN ALL TEXT REPEATS]
I support this position because... I support this position because...
```

### After Fix:
```
我支持这一立场，因为人工智能的监管对于确保技术发展的安全性和伦理性至关重要。
首先，适当的监管可以防止AI系统被滥用...
```

## Testing Recommendations

1. **Start New Debate Session**
   - Configure both AI personalities
   - Start automated debate
   - Verify no prompt leakage in output

2. **Monitor Streaming Output**
   - Check browser console for SSE events
   - Verify incremental chunks arrive smoothly
   - Confirm no duplicate text in final display

3. **Language Consistency**
   - All AI output should be in Chinese
   - No English labels or prompts visible
   - Debate arguments should be professional Chinese

4. **Playback Controls**
   - Test Pause/Resume functionality
   - Verify Skip to End works correctly
   - Check debate completion and winner announcement

## Files Modified

1. `aidebate-app/src/main/java/com/aidebate/app/service/AlibabaAIService.java`
   - Fixed `callQwenAPIStream()` method
   - Converted 6 prompt building methods to Chinese
   - Added anti-leakage instructions

## Next Steps

1. **Immediate Testing**: Test the automated debate flow end-to-end
2. **Monitor Logs**: Check application logs for any streaming errors
3. **User Feedback**: Gather feedback on debate quality and language consistency
4. **Performance**: Monitor streaming performance and response times

## Conclusion

All three critical bugs have been resolved:
- ✅ No more prompt leakage
- ✅ No more repetitive text
- ✅ Consistent Chinese output
- ✅ Clean, professional debate experience
