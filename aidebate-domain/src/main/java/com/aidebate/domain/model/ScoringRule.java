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
 * Scoring Rule domain entity
 * Defines scoring criteria for debates
 *
 * @author AI Debate Team
 */
@TableName("scoring_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringRule {
    
    @TableId(value = "rule_id", type = IdType.AUTO)
    private Long ruleId;
    private Long sessionId;
    private String criteriaName;
    private Integer maxScore;
    private BigDecimal weight;
    private String description;
    private LocalDateTime createdAt;
}
