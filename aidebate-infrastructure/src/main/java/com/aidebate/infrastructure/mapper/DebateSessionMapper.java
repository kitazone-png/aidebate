package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.DebateSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Debate Session Mapper interface
 *
 * @author AI Debate Team
 */
@Mapper
public interface DebateSessionMapper extends BaseMapper<DebateSession> {
}
