package com.aidebate.adapter.web.controller;

import com.aidebate.app.service.TopicApplicationService;
import com.aidebate.domain.model.DebateTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Topic Controller
 * Handles debate topic related endpoints
 *
 * @author AI Debate Team
 */
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TopicController {

    private final TopicApplicationService topicApplicationService;

    /**
     * Get all active debate topics
     */
    @GetMapping
    public List<DebateTopic> getActiveTopics(@RequestParam(required = false) String category) {
        return topicApplicationService.listActiveTopics(category);
    }

    /**
     * Get topic by ID
     */
    @GetMapping("/{id}")
    public DebateTopic getTopicById(@PathVariable Long id) {
        return topicApplicationService.getTopicById(id);
    }

    /**
     * Create custom topic
     */
    @PostMapping("/custom")
    public DebateTopic createCustomTopic(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String description = request.get("description");
        String category = request.getOrDefault("category", "Custom");
        Long userId = Long.parseLong(request.getOrDefault("userId", "1")); // Default user for now
        
        return topicApplicationService.createCustomTopic(title, description, category, userId);
    }

    /**
     * Generate topic with AI
     */
    @PostMapping("/generate")
    public DebateTopic generateTopic(@RequestBody Map<String, String> request) {
        String userInput = request.get("userInput");
        Long userId = Long.parseLong(request.getOrDefault("userId", "1"));
        
        return topicApplicationService.generateTopicWithAI(userInput, userId);
    }

    /**
     * Toggle topic status
     */
    @PutMapping("/{id}/activate")
    public Map<String, Object> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        boolean isActive = request.getOrDefault("isActive", true);
        topicApplicationService.toggleTopicStatus(id, isActive);
        
        return Map.of("success", true, "topicId", id, "isActive", isActive);
    }
}
