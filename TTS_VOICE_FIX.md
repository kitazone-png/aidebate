# TTS Voice ID Fix

## Issue
Audio playback was failing with error:
```
Invalid voice, SetVoice failed
```

## Root Cause
The voice IDs used in the initial implementation were not valid for Alibaba Cloud's Qwen TTS Realtime API.

## Solution
Updated voice IDs to use the correct voice names supported by `qwen3-tts-flash-realtime` model.

### Valid Voice IDs for Qwen TTS

#### Chinese Voices (zh)
| Voice ID | Name | Gender | Character |
|----------|------|--------|-----------|
| longwan | 龙婉 | Female | Professional, clear |
| longxiaochun | 龙小春 | Male | Confident, energetic |
| longxiaobai | 龙小白 | Male | Experienced, stable |
| longyuan | 龙渊 | Male | Authoritative, neutral |

#### English Voices (en)
| Voice ID | Name | Gender | Character |
|----------|------|--------|-----------|
| longwan_en | - | Female | Professional |
| longxiaobai_en | - | Male | Clear, confident |
| longyuan_en | - | Male | Authoritative |

### Voice Mapping for Debate Roles

#### Chinese (zh)
- **AFFIRMATIVE**: `longxiaochun` (龙小春 - male, confident)
- **NEGATIVE**: `longwan` (龙婉 - female, professional)
- **MODERATOR**: `longyuan` (龙渊 - male, authoritative)
- **ORGANIZER**: `longyuan` (龙渊 - formal)
- **JUDGE**: `longxiaobai` (龙小白 - experienced)

#### English (en)
- **AFFIRMATIVE**: `longxiaobai_en` (male, clear)
- **NEGATIVE**: `longwan_en` (female, professional)
- **MODERATOR**: `longyuan_en` (authoritative)
- **ORGANIZER**: `longyuan_en` (formal)
- **JUDGE**: `longxiaobai_en` (experienced)

## Voice Differentiation Strategy
- **Affirmative vs Negative**: Different genders (male vs female) to provide clear auditory distinction
- **Moderator/Organizer**: Same authoritative voice (longyuan/longyuan_en) for consistency
- **Judges**: Experienced-sounding voice (longxiaobai/longxiaobai_en)

## Code Changes
File: `aidebate-app/src/main/java/com/aidebate/app/service/VoiceAIService.java`

### Before (Invalid)
```java
VOICE_PROFILES_ZH.put("AFFIRMATIVE", "zhixiaobai");
VOICE_PROFILES_ZH.put("NEGATIVE", "zhiyan");
VOICE_PROFILES_EN.put("AFFIRMATIVE", "kenny");
```

### After (Valid)
```java
VOICE_PROFILES_ZH.put("AFFIRMATIVE", "longxiaochun");
VOICE_PROFILES_ZH.put("NEGATIVE", "longwan");
VOICE_PROFILES_EN.put("AFFIRMATIVE", "longxiaobai_en");
```

## Testing
After applying this fix:
1. Rebuild the project: `mvn clean compile`
2. Restart the application
3. Click the read-aloud button (🔊) on any debate message
4. Verify audio plays successfully
5. Test both Chinese and English content
6. Verify different voices for Affirmative vs Negative speakers

## References
- Alibaba Cloud Qwen TTS Documentation: https://help.aliyun.com/zh/dashscope/developer-reference/tts-api
- Model: `qwen3-tts-flash-realtime`
- Format: `PCM_24000HZ_MONO_16BIT`
- Mode: `server_commit`
