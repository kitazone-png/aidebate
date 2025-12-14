package com.aidebate.domain.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Debate Session domain entity
 *
 * @author AI Debate Team
 */
@TableName("debate_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateSession {
    
    /**
     * Unique session identifier
     */
    @TableId(value = "session_id", type = IdType.AUTO)
    private Long sessionId;
    
    /**
     * Reference to debate topic
     */
    private Long topicId;
    
    /**
     * Participating user (optional for AI-only debates)
     */
    private Long userId;
    
    /**
     * AI configurations for both affirmative and negative debaters
     */
    private String aiDebaterConfigs;
    
    /**
     * Auto-play speed for AI argument generation
     */
    private AutoPlaySpeed autoPlaySpeed;
    
    /**
     * Current pause state
     */
    private Boolean isPaused;
    
    /**
     * Current execution position for pause/resume
     */
    private String currentPosition;
    
    /**
     * Session status
     */
    private SessionStatus status;
    
    /**
     * Session start time
     */
    private LocalDateTime startedAt;
    
    /**
     * Session completion time
     */
    private LocalDateTime completedAt;
    
    /**
     * Affirmative side's final score
     */
    private BigDecimal finalScoreAffirmative;
    
    /**
     * Negative side's final score
     */
    private BigDecimal finalScoreNegative;
    
    /**
     * Debate winner
     */
    private Winner winner;
    
    /**
     * Record creation time
     */
    private LocalDateTime createdAt;
    
    /**
     * Auto-play speed enumeration
     */
    public enum AutoPlaySpeed {
        FAST,
        NORMAL,
        SLOW
    }
    
    /**
     * Session status enumeration
     */
    public enum SessionStatus {
        INITIALIZED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        ABORTED
    }
    
    /**
     * Winner enumeration
     */
    public enum Winner {
        AFFIRMATIVE,
        NEGATIVE,
        DRAW
    }
    
    /**
     * Check if session is in progress
     */
    public boolean isInProgress() {
        return SessionStatus.IN_PROGRESS.equals(status);
    }
    
    /**
     * Check if session is completed
     */
    public boolean isCompleted() {
        return SessionStatus.COMPLETED.equals(status);
    }
    
    /**
     * Start the debate session
     */
    public void start() {
        this.status = SessionStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }
    
    /**
     * Complete the debate session
     */
    public void complete(BigDecimal affirmativeScore, BigDecimal negativeScore, Winner winner) {
        this.status = SessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.finalScoreAffirmative = affirmativeScore;
        this.finalScoreNegative = negativeScore;
        this.winner = winner;
    }
    
    /**
     * Pause the debate session
     */
    public void pause(String position) {
        this.status = SessionStatus.PAUSED;
        this.isPaused = true;
        this.currentPosition = position;
    }
    
    /**
     * Resume the debate session
     */
    public void resume() {
        this.status = SessionStatus.IN_PROGRESS;
        this.isPaused = false;
    }
    
    /**
     * Check if session is paused
     */
    public boolean isPaused() {
        return Boolean.TRUE.equals(isPaused);
    }
    
    /**
     * Abort the debate session
     */
    public void abort() {
        this.status = SessionStatus.ABORTED;
        this.completedAt = LocalDateTime.now();
    }
}
