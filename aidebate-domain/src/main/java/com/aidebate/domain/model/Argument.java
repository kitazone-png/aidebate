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
 * Argument domain entity
 * Represents arguments submitted during debates
 *
 * @author AI Debate Team
 */
@TableName("argument")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Argument {
    
    @TableId(value = "argument_id", type = IdType.AUTO)
    private Long argumentId;
    private Long sessionId;
    private Long roleId;
    private Integer roundNumber;
    private String argumentText;
    private Boolean isPreview;
    private LocalDateTime submittedAt;
    private Integer characterCount;
    private ValidationStatus validationStatus;
    private String validationMessage;
    
    /**
     * Validation status enumeration
     */
    public enum ValidationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
