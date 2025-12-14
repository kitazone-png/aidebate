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
 * Sensitive Word domain entity
 *
 * @author AI Debate Team
 */
@TableName("sensitive_word")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveWord {
    
    /**
     * Unique word identifier
     */
    @TableId(value = "word_id", type = IdType.AUTO)
    private Long wordId;
    
    /**
     * Sensitive word or phrase
     */
    private String word;
    
    /**
     * Word category
     */
    private String category;
    
    /**
     * Severity level
     */
    private Severity severity;
    
    /**
     * Whether word is currently active
     */
    private Boolean isActive;
    
    /**
     * Admin who added the word
     */
    private Long createdBy;
    
    /**
     * Creation time
     */
    private LocalDateTime createdAt;
    
    /**
     * Last update time
     */
    private LocalDateTime updatedAt;
    
    /**
     * Severity enumeration
     */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Check if word is active
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }
    
    /**
     * Check if severity is critical
     */
    public boolean isCritical() {
        return Severity.CRITICAL.equals(severity);
    }
    
    /**
     * Check if severity is high or critical
     */
    public boolean isHighSeverity() {
        return Severity.HIGH.equals(severity) || Severity.CRITICAL.equals(severity);
    }
}
