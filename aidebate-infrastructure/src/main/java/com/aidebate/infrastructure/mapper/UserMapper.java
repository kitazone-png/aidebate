package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * User Mapper interface
 *
 * @author AI Debate Team
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
