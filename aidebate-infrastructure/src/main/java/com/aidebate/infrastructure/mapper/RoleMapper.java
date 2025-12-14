package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Role Mapper
 * Database access for role entity
 *
 * @author AI Debate Team
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
