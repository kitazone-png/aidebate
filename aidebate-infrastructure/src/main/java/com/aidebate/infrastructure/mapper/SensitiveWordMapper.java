package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.SensitiveWord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Sensitive Word Mapper interface
 *
 * @author AI Debate Team
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
    
    /**
     * Get all active sensitive words
     */
    @Select("SELECT * FROM sensitive_word WHERE is_active = true")
    List<SensitiveWord> selectAllActive();
}
