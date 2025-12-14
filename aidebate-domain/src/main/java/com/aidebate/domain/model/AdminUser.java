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
 * Admin User domain entity
 * Administrator account information
 *
 * @author AI Debate Team
 */
@TableName("admin_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUser {
    
    @TableId(value = "admin_id", type = IdType.AUTO)
    private Long adminId;
    private String username;
    private String passwordHash;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    
    /**
     * Check if password matches
     */
    public boolean checkPassword(String password) {
        // In a real system, use BCrypt or similar
        // For now, simple comparison (admin/admin as per requirements)
        return this.passwordHash != null && this.passwordHash.equals(password);
    }
    
    /**
     * Update last login time
     */
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
