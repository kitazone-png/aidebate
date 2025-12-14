package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.ModeratorMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Moderator Message Mapper
 * MyBatis Plus mapper for moderator_message table
 *
 * @author AI Debate Team
 */
@Mapper
public interface ModeratorMessageMapper extends BaseMapper<ModeratorMessage> {
}
