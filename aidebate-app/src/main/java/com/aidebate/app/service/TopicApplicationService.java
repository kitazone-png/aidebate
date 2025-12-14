package com.aidebate.app.service;

import com.aidebate.domain.model.DebateTopic;
import com.aidebate.infrastructure.mapper.DebateTopicMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Topic Application Service
 * Manages debate topic lifecycle and AI generation
 *
 * @author AI Debate Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicApplicationService {

    private final DebateTopicMapper topicMapper;
    private final AlibabaAIService alibabaAIService;
    private final ContentModerationService contentModerationService;

    /**
     * Create custom topic
     */
    @Transactional
    public DebateTopic createCustomTopic(String title, String description, String category, Long userId) {
        log.info("Creating custom topic: {}", title);

        // Validate content
        var titleValidation = contentModerationService.validateContent(title);
        if (!titleValidation.isValid()) {
            throw new RuntimeException("Title validation failed: " + String.join(", ", titleValidation.getViolatedWords()));
        }

        if (description != null && !description.isEmpty()) {
            var descValidation = contentModerationService.validateContent(description);
            if (!descValidation.isValid()) {
                throw new RuntimeException("Description validation failed: " + String.join(", ", descValidation.getViolatedWords()));
            }
        }

        DebateTopic topic = DebateTopic.builder()
                .title(title)
                .description(description)
                .source(DebateTopic.TopicSource.USER_CUSTOM)
                .category(category)
                .createdBy(userId)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        topicMapper.insert(topic);
        return topic;
    }

    /**
     * Generate topic with AI
     */
    @Transactional
    public DebateTopic generateTopicWithAI(String userInput, Long userId) {
        log.info("Generating AI topic from input: {}", userInput);

        Map<String, String> generated = alibabaAIService.generateDebateTopic(userInput);

        String title = generated.get("title");
        String description = generated.get("description");

        // Validate generated content
        var validation = contentModerationService.validateContent(title + " " + description);
        if (!validation.isValid()) {
            throw new RuntimeException("Generated content validation failed: " + String.join(", ", validation.getViolatedWords()));
        }

        DebateTopic topic = DebateTopic.builder()
                .title(title)
                .description(description)
                .source(DebateTopic.TopicSource.AI_GENERATED)
                .category("AI Generated")
                .createdBy(userId)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        topicMapper.insert(topic);
        return topic;
    }

    /**
     * List active topics
     */
    public List<DebateTopic> listActiveTopics(String category) {
        QueryWrapper<DebateTopic> wrapper = new QueryWrapper<>();
        wrapper.eq("is_active", true);
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        wrapper.orderByDesc("created_at");
        return topicMapper.selectList(wrapper);
    }

    /**
     * Get topic by ID
     */
    public DebateTopic getTopicById(Long topicId) {
        DebateTopic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new RuntimeException("Topic not found: " + topicId);
        }
        return topic;
    }

    /**
     * Toggle topic active status
     */
    @Transactional
    public void toggleTopicStatus(Long topicId, boolean isActive) {
        DebateTopic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new RuntimeException("Topic not found: " + topicId);
        }
        topic.setIsActive(isActive);
        topicMapper.updateById(topic);
    }
}
