package com.aidebate.domain.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Round Score Record domain entity
 * Records per-round scores from judges for each side
 *
 * @author AI Debate Team
 */
@TableName("round_score_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundScoreRecord {
    
    @TableId(value = "round_score_id", type = IdType.AUTO)
    private Long roundScoreId;
    
    private Long sessionId;
    
    private Integer roundNumber;
    
    private Long judgeRoleId;
    
    /**
     * Which side this score is for: AFFIRMATIVE or NEGATIVE
     */
    private String debaterSide;
    
    /**
     * Score out of 100
     */
    private BigDecimal score;
    
    /**
     * Judge's reasoning for this score
     */
    private String feedback;
    
    private LocalDateTime scoredAt;
}
