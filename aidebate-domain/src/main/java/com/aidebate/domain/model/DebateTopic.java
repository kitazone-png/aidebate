package com.aidebate.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Debate Topic domain entity
 *
 * @author AI Debate Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateTopic {
    
    /**
     * Unique topic identifier
     */
    private Long topicId;
    
    /**
     * Topic title
     */
    private String title;
    
    /**
     * Detailed description
     */
    private String description;
    
    /**
     * Topic source type
     */
    private TopicSource source;
    
    /**
     * Topic category
     */
    private String category;
    
    /**
     * User who created topic
     */
    private Long createdBy;
    
    /**
     * Creation time
     */
    private LocalDateTime createdAt;
    
    /**
     * Active status
     */
    private Boolean isActive;
    
    /**
     * Topic source enumeration
     */
    public enum TopicSource {
        HOT_TOPIC,
        USER_CUSTOM,
        AI_GENERATED
    }
    
    /**
     * Check if topic is active
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }
    
    /**
     * Validate topic
     */
    public boolean validate() {
        return title != null && !title.trim().isEmpty()
                && source != null;
    }
}
