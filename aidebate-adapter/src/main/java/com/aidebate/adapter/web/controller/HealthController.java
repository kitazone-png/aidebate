package com.aidebate.adapter.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health Check Controller
 *
 * @author AI Debate Team
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("application", "AI Debate Simulator");
        response.put("version", "1.0.0-SNAPSHOT");
        return response;
    }
}
