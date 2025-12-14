package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.AdminUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Admin User Mapper
 * Database access for admin user entity
 *
 * @author AI Debate Team
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
