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
 * Score Record domain entity
 * Records individual scores from judges
 *
 * @author AI Debate Team
 */
@TableName("score_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreRecord {
    
    @TableId(value = "score_id", type = IdType.AUTO)
    private Long scoreId;
    private Long sessionId;
    private Long argumentId;
    private Long judgeRoleId;
    private Long ruleId;
    private BigDecimal score;
    private String feedback;
    private LocalDateTime scoredAt;
}
