package com.aidebate.domain.model;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateSession {
    
    /**
     * Unique session identifier
     */
    private Long sessionId;
    
    /**
     * Reference to debate topic
     */
    private Long topicId;
    
    /**
     * Participating user
     */
    private Long userId;
    
    /**
     * User's debate side
     */
    private DebateSide userSide;
    
    /**
     * AI personality and level settings
     */
    private String aiOpponentConfig;
    
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
     * User's final score
     */
    private BigDecimal finalScoreUser;
    
    /**
     * AI's final score
     */
    private BigDecimal finalScoreAi;
    
    /**
     * Debate winner
     */
    private Winner winner;
    
    /**
     * Record creation time
     */
    private LocalDateTime createdAt;
    
    /**
     * Debate side enumeration
     */
    public enum DebateSide {
        AFFIRMATIVE,
        NEGATIVE
    }
    
    /**
     * Session status enumeration
     */
    public enum SessionStatus {
        INITIALIZED,
        IN_PROGRESS,
        COMPLETED,
        ABORTED
    }
    
    /**
     * Winner enumeration
     */
    public enum Winner {
        USER,
        AI,
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
    public void complete(BigDecimal userScore, BigDecimal aiScore, Winner winner) {
        this.status = SessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.finalScoreUser = userScore;
        this.finalScoreAi = aiScore;
        this.winner = winner;
    }
    
    /**
     * Abort the debate session
     */
    public void abort() {
        this.status = SessionStatus.ABORTED;
        this.completedAt = LocalDateTime.now();
    }
}
