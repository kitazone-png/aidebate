package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.ScoreRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Score Record Mapper
 * Database access for score record entity
 *
 * @author AI Debate Team
 */
@Mapper
public interface ScoreRecordMapper extends BaseMapper<ScoreRecord> {
}
