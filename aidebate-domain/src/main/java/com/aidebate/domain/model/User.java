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
 * User domain entity
 *
 * @author AI Debate Team
 */
@TableName("user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    /**
     * Unique user identifier
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    
    /**
     * User login name
     */
    private String username;
    
    /**
     * User email address
     */
    private String email;
    
    /**
     * Hashed password
     */
    private String passwordHash;
    
    /**
     * Account creation time
     */
    private LocalDateTime createdAt;
    
    /**
     * Last update time
     */
    private LocalDateTime updatedAt;
    
    /**
     * Account active status
     */
    private Boolean isActive;
    
    /**
     * Validate user information
     */
    public boolean validate() {
        return username != null && !username.trim().isEmpty()
                && email != null && email.contains("@")
                && passwordHash != null && !passwordHash.isEmpty();
    }
    
    /**
     * Check if user is active
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }
}
