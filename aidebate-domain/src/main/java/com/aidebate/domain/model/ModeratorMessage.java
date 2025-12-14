package com.aidebate.domain.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Moderator Message domain entity
 * Represents moderator interventions during debates
 *
 * @author AI Debate Team
 */
@TableName("moderator_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeratorMessage {
    
    /**
     * Unique message identifier
     */
    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;
    
    /**
     * Reference to debate session
     */
    private Long sessionId;
    
    /**
     * Reference to argument being discussed (optional)
     */
    private Long argumentId;
    
    /**
     * Round number in debate
     */
    private Integer roundNumber;
    
    /**
     * Type of moderator message
     */
    private MessageType messageType;
    
    /**
     * Message content
     */
    private String content;
    
    /**
     * Side that was speaking when this message was generated
     */
    private String speakerSide;
    
    /**
     * Next speaker announced (for announcements)
     */
    private String nextSpeaker;
    
    /**
     * Message creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Message type enumeration
     */
    public enum MessageType {
        SUMMARY,           // Summary of argument
        EVALUATION,        // Evaluation of argument quality
        ANNOUNCEMENT,      // Speaker announcement
        ROUND_TRANSITION   // Round completion notice
    }
}
