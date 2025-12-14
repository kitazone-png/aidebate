package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.ScoringRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Scoring Rule Mapper
 * Database access for scoring rule entity
 *
 * @author AI Debate Team
 */
@Mapper
public interface ScoringRuleMapper extends BaseMapper<ScoringRule> {
}
