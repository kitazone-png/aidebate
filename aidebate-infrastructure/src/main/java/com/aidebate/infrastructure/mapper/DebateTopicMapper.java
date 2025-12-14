package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.DebateTopic;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Debate Topic Mapper interface
 *
 * @author AI Debate Team
 */
@Mapper
public interface DebateTopicMapper extends BaseMapper<DebateTopic> {
}
