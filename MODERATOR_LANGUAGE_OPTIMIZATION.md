# Moderator Language Optimization

## Date: 2024-12-14

## Overview
Optimized the moderator speech output to respect the user's selected language preference on the frontend page, instead of always defaulting to Chinese.

## Problem Statement

### Before Optimization
The moderator AI-generated content (summary, evaluation, announcement) always used Chinese prompts regardless of the user's language selection:

```java
private String buildModeratorSummaryPrompt(String language) {
    // Always use Chinese for consistent output ❌
    return "你是一位经验丰富的辩论主持人...";
}
```

This caused issues when users selected English as their preferred language - the moderator would still respond in Chinese, creating a poor multilingual user experience.

## Solution

Updated the three moderator prompt building methods in `AlibabaAIService` to respect the `language` parameter:

1. `buildModeratorSummaryPrompt(String language)`
2. `buildModeratorEvaluationPrompt(String language)`
3. `buildModeratorAnnouncementPrompt(String language)`

## Changes Made

### File: `AlibabaAIService.java`

#### 1. Moderator Summary Prompt

**Before**:
```java
private String buildModeratorSummaryPrompt(String language) {
    // Always use Chinese for consistent output
    return "你是一位经验丰富的辩论主持人。你的任务是客观、简洁地总结辩手的发言要点，保持中立立场。" +
           "直接输出总结内容，不要包含任何提示词或格式说明。最多200字。";
}
```

**After**:
```java
private String buildModeratorSummaryPrompt(String language) {
    if ("zh".equals(language)) {
        return "你是一位经验丰富的辩论主持人。你的任务是客观、简洁地总结辩手的发言要点，保持中立立场。" +
               "直接输出总结内容，不要包含任何提示词或格式说明。最多200字。";
    } else {
        return "You are an experienced debate moderator. Your task is to objectively and concisely summarize the key points of the debater's statement, maintaining a neutral stance. " +
               "Output the summary directly without any prompts or formatting instructions. Maximum 200 words.";
    }
}
```

#### 2. Moderator Evaluation Prompt

**Before**:
```java
private String buildModeratorEvaluationPrompt(String language) {
    // Always use Chinese for consistent output
    return "你是一位公正的辩论主持人。提供建设性、平衡的评价，考虑论点的逻辑性、相关性和说服力。保持中立并提供有价值的反馈。" +
           "直接输出评价内容，不要包含任何提示词或格式说明。最多300字。";
}
```

**After**:
```java
private String buildModeratorEvaluationPrompt(String language) {
    if ("zh".equals(language)) {
        return "你是一位公正的辩论主持人。提供建设性、平衡的评价，考虑论点的逻辑性、相关性和说服力。保持中立并提供有价值的反馈。" +
               "直接输出评价内容，不要包含任何提示词或格式说明。最多300字。";
    } else {
        return "You are a fair debate moderator. Provide constructive and balanced evaluation, considering the logic, relevance, and persuasiveness of the arguments. Remain neutral and provide valuable feedback. " +
               "Output the evaluation directly without any prompts or formatting instructions. Maximum 300 words.";
    }
}
```

#### 3. Moderator Announcement Prompt

**Before**:
```java
private String buildModeratorAnnouncementPrompt(String language) {
    // Always use Chinese for consistent output
    return "你是一位专业的辩论主持人。宣布下一位发言者并提供简短的指导，帮助推进辩论。保持鼓励和建设性的语气。" +
           "直接输出宣布内容，不要包含任何提示词或格式说明。最多200字。";
}
```

**After**:
```java
private String buildModeratorAnnouncementPrompt(String language) {
    if ("zh".equals(language)) {
        return "你是一位专业的辩论主持人。宣布下一位发言者并提供简短的指导，帮助推进辩论。保持鼓励和建设性的语气。" +
               "直接输出宣布内容，不要包含任何提示词或格式说明。最多200字。";
    } else {
        return "You are a professional debate moderator. Announce the next speaker and provide brief guidance to help advance the debate. Maintain an encouraging and constructive tone. " +
               "Output the announcement directly without any prompts or formatting instructions. Maximum 200 words.";
    }
}
```

## Language Support

### Chinese (zh)
- Uses "你是一位经验丰富的辩论主持人" style prompts
- Character limits specified in Chinese: "最多200字", "最多300字"
- Maintains anti-prompt-leakage instructions: "直接输出...不要包含任何提示词或格式说明"

### English (en/default)
- Uses "You are an experienced debate moderator" style prompts
- Word limits specified in English: "Maximum 200 words", "Maximum 300 words"
- Maintains anti-prompt-leakage instructions: "Output directly without any prompts or formatting instructions"

## Prompt Translation Quality

All English prompts maintain semantic equivalence with Chinese versions:

| Chinese | English |
|---------|---------|
| 客观、简洁地总结 | objectively and concisely summarize |
| 保持中立立场 | maintaining a neutral stance |
| 建设性、平衡的评价 | constructive and balanced evaluation |
| 逻辑性、相关性和说服力 | logic, relevance, and persuasiveness |
| 保持鼓励和建设性的语气 | maintain an encouraging and constructive tone |

## Frontend Language Flow

The language parameter flows from frontend to backend as follows:

1. **Frontend** (`app-chat.js`):
   ```javascript
   appState.language = 'en'; // or 'zh'
   ```

2. **API Call**:
   ```javascript
   fetch(`/api/debates/${sessionId}/stream-debate?language=${language}`)
   ```

3. **Backend Controller** (`DebateSessionController`):
   ```java
   public SseEmitter streamDebate(@RequestParam(required = false, defaultValue = "en") String language)
   ```

4. **Orchestration Service** (`DebateOrchestrationService`):
   ```java
   moderatorService.generateArgumentSummaryStream(..., language, callback);
   ```

5. **Moderator Service** (`ModeratorService`):
   ```java
   alibabaAIService.generateArgumentSummaryStream(..., language, callback);
   ```

6. **AI Service** (`AlibabaAIService`):
   ```java
   String systemPrompt = buildModeratorSummaryPrompt(language);
   ```

## Static Messages

Note that static moderator messages in `ModeratorService` already support both languages:

```java
public void generateOrganizerRulesStream(Long sessionId, String language, ...) {
    String rules;
    if ("zh".equals(language)) {
        rules = "欢迎参加本次辩论赛!...";
    } else {
        rules = "Welcome to this debate!...";
    }
    callback.onChunk(rules, true);
}
```

These static messages include:
- Organizer rules
- Debate introduction
- Judge feedback templates
- Winner announcement

## Benefits

✅ **Multilingual Support**: Users can now enjoy debates in their preferred language
✅ **Consistent UX**: All moderator output (AI-generated and static) respects language preference
✅ **Professional Quality**: Both Chinese and English prompts maintain high quality and clarity
✅ **Anti-Leakage**: Both languages include instructions to prevent prompt leakage
✅ **Semantic Equivalence**: English prompts accurately translate Chinese intent

## Testing Recommendations

### Test Case 1: Chinese Language Debate
1. Set frontend language to Chinese (zh)
2. Start automated debate
3. Verify moderator summary, evaluation, and announcements are in Chinese
4. Check for proper Chinese grammar and professional tone

### Test Case 2: English Language Debate
1. Set frontend language to English (en)
2. Start automated debate
3. Verify moderator summary, evaluation, and announcements are in English
4. Check for proper English grammar and professional tone

### Test Case 3: Language Switching
1. Start debate in Chinese
2. Note: Language switching mid-debate is not currently supported
3. Language is set at session initialization and persists throughout

## Compilation Status

✅ No compilation errors
✅ All changes syntactically correct
✅ Backward compatible with existing code

## Files Modified

1. `aidebate-app/src/main/java/com/aidebate/app/service/AlibabaAIService.java`
   - Modified `buildModeratorSummaryPrompt()` - Added English support
   - Modified `buildModeratorEvaluationPrompt()` - Added English support
   - Modified `buildModeratorAnnouncementPrompt()` - Added English support

## Next Steps

1. **Testing**: Conduct thorough testing of both Chinese and English debates
2. **User Feedback**: Gather feedback on English moderator message quality
3. **Enhancement**: Consider adding more language options (e.g., Spanish, French)
4. **Documentation**: Update user manual with language selection instructions

## Conclusion

The moderator speech output now properly respects the user's language preference, providing a truly bilingual debate experience. This enhancement significantly improves the platform's accessibility for international users while maintaining the high-quality Chinese experience for local users.
