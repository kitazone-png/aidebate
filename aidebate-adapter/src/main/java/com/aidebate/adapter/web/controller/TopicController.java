package com.aidebate.adapter.web.controller;

import com.aidebate.domain.model.DebateTopic;
import com.aidebate.infrastructure.mapper.DebateTopicMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Topic Controller
 * Handles debate topic related endpoints
 *
 * @author AI Debate Team
 */
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final DebateTopicMapper debateTopicMapper;

    /**
     * Get all active debate topics
     */
    @GetMapping
    public List<DebateTopic> getActiveTopics() {
        QueryWrapper<DebateTopic> wrapper = new QueryWrapper<>();
        wrapper.eq("is_active", true);
        wrapper.orderByDesc("created_at");
        return debateTopicMapper.selectList(wrapper);
    }
}
