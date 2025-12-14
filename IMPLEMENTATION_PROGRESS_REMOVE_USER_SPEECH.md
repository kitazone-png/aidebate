# Remove User Speech Functionality - Implementation Progress Report

## Implementation Status: 75% Complete

### ✅ Completed Tasks

#### Phase 1: Backend - Database and Domain Models (100% Complete)

1. **Database Schema Updates** ✅
   - Created migration script: `migration_v2_remove_user_speech.sql`
   - Updated `schema.sql` with new structure:
     - Removed `user_side` field
     - Renamed `ai_opponent_config` to `ai_debater_configs`
     - Made `user_id` nullable
     - Added `auto_play_speed`, `is_paused`, `current_position` fields
     - Changed winner enum to AFFIRMATIVE/NEGATIVE/DRAW
     - Renamed score fields to `final_score_affirmative` and `final_score_negative`
     - Added PAUSED status to session status enum

2. **Domain Model Updates** ✅
   - `DebateSession.java` fully updated:
     - Removed `DebateSide` enum and `userSide` field
     - Renamed `aiOpponentConfig` to `aiDebaterConfigs`
     - Added `AutoPlaySpeed` enum (FAST, NORMAL, SLOW)
     - Added `isPaused` and `currentPosition` fields
     - Updated `Winner` enum (AFFIRMATIVE, NEGATIVE, DRAW)
     - Added pause(), resume(), isPaused() methods
     - Updated complete() method signature

3. **Orchestration Service** ✅
   - Created `DebateOrchestrationService.java` (545 lines)
   - Implements complete automated debate flow:
     - streamAutomatedDebate() - main SSE streaming orchestration
     - generateOpeningSequence() - organizer rules + moderator intro
     - generateRound() - full round with both AI arguments + moderator feedback
     - generateJudgingSequence() - final scoring + winner announcement
     - pauseDebate(), resumeFromPosition(), skipToEnd()
   - Supports all AutoPlaySpeed options
   - Implements symmetric moderator intervention for both sides

4. **AI Service Updates** ✅
   - `AlibabaAIService.java` updated with:
     - generateDebateArgumentStream() - unified method for both AI debaters
     - buildDebateArgumentSystemPrompt() - prompts for AI vs AI
     - buildDebateArgumentUserPrompt() - context-aware prompts
   - Maintains streaming with blocking for sequential execution

5. **Moderator Service Updates** ✅
   - `ModeratorService.java` updated with new streaming methods:
     - generateOrganizerRulesStream()
     - generateDebateIntroductionStream()
     - generateJudgeFeedbackStream()
     - generateWinnerAnnouncementStream()
   - Fixed references to removed `getUserSide()` method

#### Phase 1.5: Backend - Service Layer (75% Complete)

6. **DebateSessionService** ⚠️ PARTIALLY COMPLETE
   - ✅ Updated `initializeSession()` to accept dual AI configs
   - ✅ Added `createAIRoles()` method
   - ⚠️ **REMAINING:** Need to remove/comment out user-specific methods:
     - submit User Argument() - line ~106
     - submitUserArgumentStream() - line ~256
     - simulateUserArgument() - line ~214
   - ⚠️ **REMAINING:** Update getCurrentScores() to use affirmative/negative instead of user/AI
   - ⚠️ **REMAINING:** Update helper methods that reference getUserSide() or getAiOpponentConfig()

### 🔄 In Progress / Remaining Tasks

#### Phase 1.6: Backend - Controller Updates (NOT STARTED)

7. **DebateSessionController** ⚠️ NOT STARTED
   - Remove endpoints:
     - POST `/api/debates/{sessionId}/submit-argument`
     - POST `/api/debates/{sessionId}/submit-argument-stream`
     - POST `/api/debates/{sessionId}/simulate-argument`
   - Modify endpoints:
     - POST `/api/debates/init` - accept dual AI configs
     - POST `/api/debates/{sessionId}/start` - trigger orchestration
   - Add endpoints:
     - POST `/api/debates/{sessionId}/stream-debate` - SSE streaming
     - POST `/api/debates/{sessionId}/pause`
     - POST `/api/debates/{sessionId}/resume`
     - POST `/api/debates/{sessionId}/skip-to-end`

#### Phase 2: Frontend Updates (NOT STARTED)

8. **Frontend Configuration UI** ⚠️ NOT STARTED
   - Remove: Side selection buttons
   - Add: Dual AI configuration panels
   - Add: Auto-play speed selector
   - Update: Session initialization API call

9. **Frontend Debate View** ⚠️ NOT STARTED
   - Remove: Argument input text area
   - Remove: Character counter
   - Remove: Submit button
   - Add: Play/Pause button
   - Add: Skip to End button
   - Update: Display both AI debater configs
   - Update: Neutral observer perspective

10. **Frontend SSE Event Handling** ⚠️ NOT STARTED
    - Update event handlers for new flow:
      - debate_start, organizer_rules, moderator_introduction
      - ai_argument (with side parameter)
      - judging_start, judge_feedback, winner_announcement
      - debate_complete
    - Remove user_argument event handling
    - Update state management (remove userSide, isSubmitting, etc.)

11. **i18n Labels** ⚠️ NOT STARTED
    - Update `i18n.en.json` and `i18n.zh.json`:
      - Change "You" → "Affirmative"
      - Change "AI Opponent" → "Negative"
      - Change "Submit" → "Start Debate"
      - Add "Pause", "Resume", "Skip to End" labels
      - Update score labels

### 🐛 Known Issues to Resolve

1. **DebateSessionService Compilation Errors:**
   - Multiple references to removed `getUserSide()` method
   - Multiple references to removed `getAiOpponentConfig()` method
   - References to removed `DebateSession.DebateSide` enum
   - Methods: submitUserArgument(), simulateUserArgument(), submitUserArgumentStream()
   - Helper methods: getUserArguments(), getAIArguments(), generateAIResponseWithContext()

2. **ScoringService Updates Needed:**
   - Change `getTotalScoreForUser()` → `getTotalScoreForSide(sessionId, "AFFIRMATIVE")`
   - Change `getTotalScoreForAI()` → `getTotalScoreForSide(sessionId, "NEGATIVE")`

### 📋 Completion Checklist

**Backend:**
- [x] Database schema migration
- [x] DebateSession domain model
- [x] DebateOrchestrationService created
- [x] AlibabaAIService updated
- [x] ModeratorService updated
- [ ] DebateSessionService cleanup (remove user methods)
- [ ] ScoringService updates
- [ ] DebateSessionController updates

**Frontend:**
- [ ] Configuration UI updates
- [ ] Debate view updates
- [ ] SSE event handling updates
- [ ] i18n labels updates

**Testing:**
- [ ] Backend compilation successful
- [ ] API endpoints tested
- [ ] Frontend integration tested
- [ ] End-to-end debate flow tested

### 🎯 Next Steps to Complete

1. **Immediate (High Priority):**
   - Comment out or remove user-specific methods in DebateSessionService
   - Update getCurrentScores() method
   - Update ScoringService methods
   - Ensure backend compiles successfully

2. **Controller Updates:**
   - Remove user submission endpoints from DebateSessionController
   - Add new automated debate endpoints
   - Wire up DebateOrchestrationService

3. **Frontend Updates:**
   - Update all UI components for observer mode
   - Implement new SSE event handlers
   - Update i18n files

4. **Integration Testing:**
   - Test complete automated debate flow
   - Verify all SSE events stream correctly
   - Test pause/resume functionality

### 💡 Design Decisions Made

1. **Dual AI Configuration:** Each side (Affirmative/Negative) has independent AI personality and expertise settings
2. **Auto-Play Speed:** Three levels (Fast/Normal/Slow) control delay between AI arguments
3. **Pause/Resume:** State is preserved with position tracking for smooth resumption
4. **Symmetric Moderation:** Moderator provides feedback for both AI debaters equally
5. **Streaming Architecture:** Maintained blocking reactive streams for sequential execution
6. **Role Management:** All 7 roles (Organizer, Moderator, 3 Judges, 2 Debaters) are AI-driven

### 📊 Estimated Remaining Work

- **Backend Completion:** 2-3 hours
  - Service cleanup: 1 hour
  - Controller updates: 1 hour
  - Testing: 1 hour

- **Frontend Completion:** 3-4 hours
  - UI component updates: 2 hours
  - Event handling: 1 hour
  - Testing: 1 hour

**Total Estimated Time to Complete:** 5-7 hours

### 🔗 Key Files Modified

**Created:**
- `aidebate-start/src/main/resources/db/migration_v2_remove_user_speech.sql`
- `aidebate-app/src/main/java/com/aidebate/app/service/DebateOrchestrationService.java`

**Modified:**
- `aidebate-start/src/main/resources/db/schema.sql`
- `aidebate-domain/src/main/java/com/aidebate/domain/model/DebateSession.java`
- `aidebate-app/src/main/java/com/aidebate/app/service/AlibabaAIService.java`
- `aidebate-app/src/main/java/com/aidebate/app/service/ModeratorService.java`
- `aidebate-app/src/main/java/com/aidebate/app/service/DebateSessionService.java` (partial)

**To Be Modified:**
- `aidebate-adapter/src/main/java/com/aidebate/adapter/web/controller/DebateSessionController.java`
- `aidebate-app/src/main/java/com/aidebate/app/service/ScoringService.java`
- `aidebate-start/src/main/resources/static/index.html`
- `aidebate-start/src/main/resources/static/index-chat.html`
- `aidebate-start/src/main/resources/static/app.js`
- `aidebate-start/src/main/resources/static/app-chat.js`
- `aidebate-start/src/main/resources/static/i18n.en.json`
- `aidebate-start/src/main/resources/static/i18n.zh.json`

---

**Implementation Date:** December 14, 2025  
**Status:** 75% Complete - Backend foundation solid, remaining work is cleanup and frontend integration
