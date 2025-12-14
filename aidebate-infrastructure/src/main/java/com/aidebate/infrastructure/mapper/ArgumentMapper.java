package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.Argument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Argument Mapper
 * Database access for argument entity
 *
 * @author AI Debate Team
 */
@Mapper
public interface ArgumentMapper extends BaseMapper<Argument> {
}
