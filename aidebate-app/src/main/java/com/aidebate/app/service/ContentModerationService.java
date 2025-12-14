package com.aidebate.app.service;

import com.aidebate.domain.model.SensitiveWord;
import com.aidebate.infrastructure.mapper.SensitiveWordMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Content Moderation Service
 * Validates user input and AI-generated content against sensitive word dictionary
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private final SensitiveWordMapper sensitiveWordMapper;
    
    /**
     * Cache for sensitive words (1 hour TTL)
     */
    private final LoadingCache<String, List<SensitiveWord>> sensitiveWordCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(new CacheLoader<String, List<SensitiveWord>>() {
                @Override
                public List<SensitiveWord> load(String key) {
                    return sensitiveWordMapper.selectAllActive();
                }
            });

    /**
     * Validate content against sensitive word dictionary
     *
     * @param text Content to validate
     * @return Validation result
     */
    public ValidationResult validateContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return ValidationResult.valid();
        }

        try {
            List<SensitiveWord> sensitiveWords = sensitiveWordCache.get("active");
            List<String> violations = new ArrayList<>();
            SensitiveWord.Severity maxSeverity = null;

            String lowerText = text.toLowerCase();

            for (SensitiveWord word : sensitiveWords) {
                if (word.isActive()) {
                    String pattern = word.getWord().toLowerCase();
                    
                    // Simple pattern matching (case-insensitive)
                    if (lowerText.contains(pattern)) {
                        violations.add(word.getWord());
                        
                        if (maxSeverity == null || word.getSeverity().ordinal() > maxSeverity.ordinal()) {
                            maxSeverity = word.getSeverity();
                        }
                    }
                }
            }

            if (!violations.isEmpty()) {
                return ValidationResult.invalid(violations, maxSeverity);
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            log.error("Error validating content", e);
            // On error, allow content but log the issue
            return ValidationResult.valid();
        }
    }

    /**
     * Invalidate the sensitive word cache
     */
    public void invalidateCache() {
        sensitiveWordCache.invalidateAll();
        log.info("Sensitive word cache invalidated");
    }

    /**
     * Validation result
     */
    @Data
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private List<String> violatedWords;
        private SensitiveWord.Severity maxSeverity;

        public static ValidationResult valid() {
            return new ValidationResult(true, new ArrayList<>(), null);
        }

        public static ValidationResult invalid(List<String> violations, SensitiveWord.Severity severity) {
            return new ValidationResult(false, violations, severity);
        }

        public boolean isCriticalViolation() {
            return maxSeverity != null && SensitiveWord.Severity.CRITICAL.equals(maxSeverity);
        }

        public boolean isHighSeverityViolation() {
            return maxSeverity != null && 
                   (SensitiveWord.Severity.HIGH.equals(maxSeverity) || 
                    SensitiveWord.Severity.CRITICAL.equals(maxSeverity));
        }
    }
}
