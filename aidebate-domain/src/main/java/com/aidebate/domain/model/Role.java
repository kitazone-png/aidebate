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
 * Role domain entity
 * Represents roles in debate sessions
 *
 * @author AI Debate Team
 */
@TableName("role")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    
    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;
    private Long sessionId;
    private RoleType roleType;
    private Boolean isAi;
    private Long assignedUserId;
    private String aiConfig; // JSON string
    private LocalDateTime createdAt;
    
    /**
     * Role type enumeration
     */
    public enum RoleType {
        ORGANIZER,
        MODERATOR,
        JUDGE_1,
        JUDGE_2,
        JUDGE_3,
        AFFIRMATIVE,
        NEGATIVE
    }
}
