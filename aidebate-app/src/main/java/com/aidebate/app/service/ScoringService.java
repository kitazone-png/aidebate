package com.aidebate.app.service;

import com.aidebate.domain.model.*;
import com.aidebate.infrastructure.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Scoring Service
 * Manages scoring rules and judge scoring process
 * NOW SUPPORTS PER-ROUND SCORING (v3)
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final ScoringRuleMapper scoringRuleMapper;
    private final ScoreRecordMapper scoreRecordMapper;
    private final RoleMapper roleMapper;
    private final ArgumentMapper argumentMapper;
    private final AlibabaAIService alibabaAIService;
    private final RoundScoreRecordMapper roundScoreRecordMapper;
    private final DebateTopicMapper topicMapper;
    private final DebateSessionMapper debateSessionMapper;

    /**
     * Create scoring rules for a session
     * Three criteria: Logic (40%), Persuasiveness (35%), Fluency (25%)
     */
    @Transactional
    public void createScoringRules(Long sessionId) {
        log.info("Creating scoring rules for session: {}", sessionId);

        createRule(sessionId, "Logic", 100, new BigDecimal("0.40"), 
                "Evaluates the logical structure, reasoning, and evidence quality");
        createRule(sessionId, "Persuasiveness", 100, new BigDecimal("0.35"),
                "Assesses the argument's convincing power and rhetorical effectiveness");
        createRule(sessionId, "Fluency", 100, new BigDecimal("0.25"),
                "Measures clarity, coherence, and linguistic quality");
    }

    /**
     * Score an argument by all three AI judges
     */
    @Transactional
    public void scoreArgument(Long argumentId, Long sessionId) {
        log.info("Scoring argument: {} in session: {}", argumentId, sessionId);

        Argument argument = argumentMapper.selectById(argumentId);
        if (argument == null) {
            throw new RuntimeException("Argument not found: " + argumentId);
        }

        // Get all judges
        List<Role> judges = getJudges(sessionId);
        
        // Get scoring rules
        List<ScoringRule> rules = getScoringRules(sessionId);

        // Each judge scores based on each criterion
        for (Role judge : judges) {
            for (ScoringRule rule : rules) {
                Map<String, Object> judgment = alibabaAIService.judgeArgument(
                        argument.getArgumentText(),
                        rule.getCriteriaName(),
                        rule.getMaxScore(),
                        rule.getDescription()
                );

                ScoreRecord record = ScoreRecord.builder()
                        .sessionId(sessionId)
                        .argumentId(argumentId)
                        .judgeRoleId(judge.getRoleId())
                        .ruleId(rule.getRuleId())
                        .score(BigDecimal.valueOf((Double) judgment.get("score")))
                        .feedback((String) judgment.get("feedback"))
                        .scoredAt(LocalDateTime.now())
                        .build();

                scoreRecordMapper.insert(record);
            }
        }
    }

    /**
     * Calculate total weighted score for a list of arguments
     */
    public BigDecimal calculateTotalScore(List<Long> argumentIds) {
        if (argumentIds == null || argumentIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;

        for (Long argumentId : argumentIds) {
            BigDecimal argumentScore = calculateArgumentScore(argumentId);
            total = total.add(argumentScore);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate weighted score for a single argument
     */
    public BigDecimal calculateArgumentScore(Long argumentId) {
        // Get all score records for this argument
        QueryWrapper<ScoreRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("argument_id", argumentId);
        List<ScoreRecord> scores = scoreRecordMapper.selectList(wrapper);

        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Group scores by rule (criterion)
        Map<Long, List<ScoreRecord>> scoresByRule = scores.stream()
                .collect(java.util.stream.Collectors.groupingBy(ScoreRecord::getRuleId));

        BigDecimal weightedTotal = BigDecimal.ZERO;

        // For each criterion, average the 3 judge scores and apply weight
        for (Map.Entry<Long, List<ScoreRecord>> entry : scoresByRule.entrySet()) {
            Long ruleId = entry.getKey();
            List<ScoreRecord> ruleScores = entry.getValue();

            ScoringRule rule = scoringRuleMapper.selectById(ruleId);

            // Average of 3 judges for this criterion
            BigDecimal avgScore = ruleScores.stream()
                    .map(ScoreRecord::getScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(ruleScores.size()), 2, RoundingMode.HALF_UP);

            // Apply weight
            BigDecimal weightedScore = avgScore.multiply(rule.getWeight());
            weightedTotal = weightedTotal.add(weightedScore);
        }

        return weightedTotal.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get score breakdown for an argument
     */
    public Map<String, Object> getArgumentScoreBreakdown(Long argumentId) {
        QueryWrapper<ScoreRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("argument_id", argumentId);
        List<ScoreRecord> scores = scoreRecordMapper.selectList(wrapper);

        Map<String, Object> breakdown = new java.util.HashMap<>();
        breakdown.put("argumentId", argumentId);
        breakdown.put("totalScore", calculateArgumentScore(argumentId));
        breakdown.put("judgeScores", scores);

        return breakdown;
    }

    // ========== PER-ROUND SCORING METHODS (v3) ==========

    /**
     * Score both sides after a round completes
     * This is the main entry point for per-round scoring
     */
    @Transactional
    public Map<String, BigDecimal> scoreRound(Long sessionId, Integer roundNumber, String language) {
        log.info("Scoring round {} for session {}", roundNumber, sessionId);

        // Get session context
        DebateSession session = debateSessionMapper.selectById(sessionId);
        DebateTopic topic = topicMapper.selectById(session.getTopicId());
        
        // Get arguments for this round
        Argument affirmativeArg = getArgumentForRound(sessionId, roundNumber, "AFFIRMATIVE");
        Argument negativeArg = getArgumentForRound(sessionId, roundNumber, "NEGATIVE");
        
        if (affirmativeArg == null || negativeArg == null) {
            log.warn("Missing arguments for round {}, cannot score", roundNumber);
            Map<String, BigDecimal> result = new HashMap<>();
            result.put("affirmativeScore", BigDecimal.ZERO);
            result.put("negativeScore", BigDecimal.ZERO);
            return result;
        }
        
        // Get previous round context for better evaluation
        List<String> previousContext = getPreviousRoundContext(sessionId, roundNumber);
        
        // Get all judges
        List<Role> judges = getJudges(sessionId);
        
        // ========== PARALLEL SCORING EXECUTION ==========
        // Create list of all scoring tasks (3 judges × 2 sides = 6 tasks)
        List<CompletableFuture<RoundScoreRecord>> scoringTasks = new ArrayList<>();
        
        for (Role judge : judges) {
            int judgeNumber = getJudgeNumber(judge);
            
            // Task for scoring AFFIRMATIVE side (async)
            CompletableFuture<RoundScoreRecord> affirmativeTask = CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, Object> affirmativeEval = alibabaAIService.evaluateRoundPerformance(
                        "AFFIRMATIVE",
                        roundNumber,
                        topic.getTitle(),
                        affirmativeArg.getArgumentText(),
                        negativeArg.getArgumentText(),
                        previousContext,
                        language,
                        judgeNumber
                    );
                    
                    return RoundScoreRecord.builder()
                        .sessionId(sessionId)
                        .roundNumber(roundNumber)
                        .judgeRoleId(judge.getRoleId())
                        .debaterSide("AFFIRMATIVE")
                        .score(BigDecimal.valueOf((Double) affirmativeEval.get("score")))
                        .feedback((String) affirmativeEval.get("feedback"))
                        .scoredAt(LocalDateTime.now())
                        .build();
                } catch (Exception e) {
                    log.error("Error scoring AFFIRMATIVE for judge {}", judgeNumber, e);
                    // Return fallback score
                    return RoundScoreRecord.builder()
                        .sessionId(sessionId)
                        .roundNumber(roundNumber)
                        .judgeRoleId(judge.getRoleId())
                        .debaterSide("AFFIRMATIVE")
                        .score(new BigDecimal("75.00"))
                        .feedback("Evaluation error, default score applied")
                        .scoredAt(LocalDateTime.now())
                        .build();
                }
            });
            
            // Task for scoring NEGATIVE side (async)
            CompletableFuture<RoundScoreRecord> negativeTask = CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, Object> negativeEval = alibabaAIService.evaluateRoundPerformance(
                        "NEGATIVE",
                        roundNumber,
                        topic.getTitle(),
                        negativeArg.getArgumentText(),
                        affirmativeArg.getArgumentText(),
                        previousContext,
                        language,
                        judgeNumber
                    );
                    
                    return RoundScoreRecord.builder()
                        .sessionId(sessionId)
                        .roundNumber(roundNumber)
                        .judgeRoleId(judge.getRoleId())
                        .debaterSide("NEGATIVE")
                        .score(BigDecimal.valueOf((Double) negativeEval.get("score")))
                        .feedback((String) negativeEval.get("feedback"))
                        .scoredAt(LocalDateTime.now())
                        .build();
                } catch (Exception e) {
                    log.error("Error scoring NEGATIVE for judge {}", judgeNumber, e);
                    // Return fallback score
                    return RoundScoreRecord.builder()
                        .sessionId(sessionId)
                        .roundNumber(roundNumber)
                        .judgeRoleId(judge.getRoleId())
                        .debaterSide("NEGATIVE")
                        .score(new BigDecimal("75.00"))
                        .feedback("Evaluation error, default score applied")
                        .scoredAt(LocalDateTime.now())
                        .build();
                }
            });
            
            scoringTasks.add(affirmativeTask);
            scoringTasks.add(negativeTask);
        }
        
        // Wait for all scoring tasks to complete
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
            scoringTasks.toArray(new CompletableFuture[0])
        );
        
        // Block until all tasks complete and collect results
        List<RoundScoreRecord> scoreRecords = allTasks.thenApply(v -> 
            scoringTasks.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
        ).join();
        
        // Batch insert all score records
        for (RoundScoreRecord record : scoreRecords) {
            roundScoreRecordMapper.insert(record);
        }
        
        // Calculate round averages
        BigDecimal affirmativeScore = calculateRoundAverage(sessionId, roundNumber, "AFFIRMATIVE");
        BigDecimal negativeScore = calculateRoundAverage(sessionId, roundNumber, "NEGATIVE");
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("affirmativeScore", affirmativeScore);
        result.put("negativeScore", negativeScore);
        
        return result;
    }

    /**
     * Calculate average score for a side in a specific round
     */
    public BigDecimal calculateRoundAverage(Long sessionId, Integer roundNumber, String side) {
        List<RoundScoreRecord> scores = roundScoreRecordMapper.selectBySessionRoundAndSide(
            sessionId, roundNumber, side
        );
        
        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = scores.stream()
            .map(RoundScoreRecord::getScore)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Get cumulative scores for both sides across all rounds
     */
    public Map<String, BigDecimal> getCumulativeScores(Long sessionId) {
        // Get all round scores for AFFIRMATIVE
        List<RoundScoreRecord> affirmativeScores = roundScoreRecordMapper.selectBySideAllRounds(
            sessionId, "AFFIRMATIVE"
        );
        
        // Calculate cumulative score by averaging judges per round, then summing rounds
        Map<Integer, List<RoundScoreRecord>> affirmativeByRound = new HashMap<>();
        for (RoundScoreRecord score : affirmativeScores) {
            affirmativeByRound.computeIfAbsent(score.getRoundNumber(), k -> new ArrayList<>()).add(score);
        }
        
        BigDecimal affirmativeTotal = affirmativeByRound.values().stream()
            .map(roundScores -> {
                BigDecimal sum = roundScores.stream()
                    .map(RoundScoreRecord::getScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return sum.divide(BigDecimal.valueOf(roundScores.size()), 2, RoundingMode.HALF_UP);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Get all round scores for NEGATIVE
        List<RoundScoreRecord> negativeScores = roundScoreRecordMapper.selectBySideAllRounds(
            sessionId, "NEGATIVE"
        );
        
        Map<Integer, List<RoundScoreRecord>> negativeByRound = new HashMap<>();
        for (RoundScoreRecord score : negativeScores) {
            negativeByRound.computeIfAbsent(score.getRoundNumber(), k -> new ArrayList<>()).add(score);
        }
        
        BigDecimal negativeTotal = negativeByRound.values().stream()
            .map(roundScores -> {
                BigDecimal sum = roundScores.stream()
                    .map(RoundScoreRecord::getScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return sum.divide(BigDecimal.valueOf(roundScores.size()), 2, RoundingMode.HALF_UP);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("affirmativeTotal", affirmativeTotal.setScale(2, RoundingMode.HALF_UP));
        result.put("negativeTotal", negativeTotal.setScale(2, RoundingMode.HALF_UP));
        result.put("maxPossible", new BigDecimal("500.00"));
        
        return result;
    }

    /**
     * Get detailed score breakdown for a specific round
     */
    public Map<String, Object> getRoundScoreBreakdown(Long sessionId, Integer roundNumber) {
        List<RoundScoreRecord> roundScores = roundScoreRecordMapper.selectBySessionAndRound(
            sessionId, roundNumber
        );
        
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("sessionId", sessionId);
        breakdown.put("roundNumber", roundNumber);
        
        // Group by side
        List<RoundScoreRecord> affirmativeScores = new ArrayList<>();
        List<RoundScoreRecord> negativeScores = new ArrayList<>();
        
        for (RoundScoreRecord score : roundScores) {
            if ("AFFIRMATIVE".equals(score.getDebaterSide())) {
                affirmativeScores.add(score);
            } else {
                negativeScores.add(score);
            }
        }
        
        breakdown.put("affirmativeScores", affirmativeScores);
        breakdown.put("negativeScores", negativeScores);
        breakdown.put("affirmativeAverage", calculateRoundAverage(sessionId, roundNumber, "AFFIRMATIVE"));
        breakdown.put("negativeAverage", calculateRoundAverage(sessionId, roundNumber, "NEGATIVE"));
        
        return breakdown;
    }

    // ========== Private Helper Methods ==========

    private void createRule(Long sessionId, String name, int maxScore, BigDecimal weight, String description) {
        ScoringRule rule = ScoringRule.builder()
                .sessionId(sessionId)
                .criteriaName(name)
                .maxScore(maxScore)
                .weight(weight)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();

        scoringRuleMapper.insert(rule);
    }

    private List<Role> getJudges(Long sessionId) {
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.in("role_type", "JUDGE_1", "JUDGE_2", "JUDGE_3");
        return roleMapper.selectList(wrapper);
    }

    private List<ScoringRule> getScoringRules(Long sessionId) {
        QueryWrapper<ScoringRule> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return scoringRuleMapper.selectList(wrapper);
    }
    
    /**
     * Get argument for a specific round and side
     */
    private Argument getArgumentForRound(Long sessionId, int roundNumber, String side) {
        QueryWrapper<Argument> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.eq("round_number", roundNumber);
        
        // Get role for the side
        QueryWrapper<Role> roleWrapper = new QueryWrapper<>();
        roleWrapper.eq("session_id", sessionId);
        
        if ("AFFIRMATIVE".equals(side)) {
            roleWrapper.eq("role_type", "AFFIRMATIVE");
        } else {
            roleWrapper.eq("role_type", "NEGATIVE");
        }
        
        Role role = roleMapper.selectOne(roleWrapper);
        if (role == null) {
            return null;
        }
        
        wrapper.eq("role_id", role.getRoleId());
        
        List<Argument> arguments = argumentMapper.selectList(wrapper);
        return arguments.isEmpty() ? null : arguments.get(0);
    }
    
    /**
     * Get previous round arguments for context
     */
    private List<String> getPreviousRoundContext(Long sessionId, int currentRound) {
        List<String> context = new ArrayList<>();
        
        // Get up to 2 previous rounds
        for (int round = Math.max(1, currentRound - 2); round < currentRound; round++) {
            Argument affirmativeArg = getArgumentForRound(sessionId, round, "AFFIRMATIVE");
            Argument negativeArg = getArgumentForRound(sessionId, round, "NEGATIVE");
            
            if (affirmativeArg != null) {
                context.add(String.format("Round %d Affirmative: %s", round, 
                    affirmativeArg.getArgumentText().substring(0, Math.min(100, affirmativeArg.getArgumentText().length())) + "..."));
            }
            
            if (negativeArg != null) {
                context.add(String.format("Round %d Negative: %s", round,
                    negativeArg.getArgumentText().substring(0, Math.min(100, negativeArg.getArgumentText().length())) + "..."));
            }
        }
        
        return context;
    }
    
    /**
     * Extract judge number from role
     */
    private int getJudgeNumber(Role judge) {
        Role.RoleType roleType = judge.getRoleType();
        if (roleType == Role.RoleType.JUDGE_1) return 1;
        if (roleType == Role.RoleType.JUDGE_2) return 2;
        if (roleType == Role.RoleType.JUDGE_3) return 3;
        return 1; // Default
    }
}
