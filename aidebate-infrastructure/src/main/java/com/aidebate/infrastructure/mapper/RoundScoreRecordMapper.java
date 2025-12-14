package com.aidebate.infrastructure.mapper;

import com.aidebate.domain.model.RoundScoreRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Round Score Record Mapper
 * Handles database operations for per-round judge scores
 *
 * @author AI Debate Team
 */
@Mapper
public interface RoundScoreRecordMapper extends BaseMapper<RoundScoreRecord> {
    
    /**
     * Get all scores for a specific round
     */
    @Select("SELECT * FROM round_score_record WHERE session_id = #{sessionId} AND round_number = #{roundNumber}")
    List<RoundScoreRecord> selectBySessionAndRound(@Param("sessionId") Long sessionId, @Param("roundNumber") Integer roundNumber);
    
    /**
     * Get all scores for a side across all rounds
     */
    @Select("SELECT * FROM round_score_record WHERE session_id = #{sessionId} AND debater_side = #{side} ORDER BY round_number")
    List<RoundScoreRecord> selectBySideAllRounds(@Param("sessionId") Long sessionId, @Param("side") String side);
    
    /**
     * Get scores for a specific side in a specific round
     */
    @Select("SELECT * FROM round_score_record WHERE session_id = #{sessionId} AND round_number = #{roundNumber} AND debater_side = #{side}")
    List<RoundScoreRecord> selectBySessionRoundAndSide(@Param("sessionId") Long sessionId, @Param("roundNumber") Integer roundNumber, @Param("side") String side);
}
