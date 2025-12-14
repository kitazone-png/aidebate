package com.aidebate.adapter.web.controller;

import com.aidebate.app.service.AdminService;
import com.aidebate.domain.model.SensitiveWord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin Controller
 * Handles admin authentication and management operations
 *
 * @author AI Debate Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;

    /**
     * Admin login
     * POST /api/admin/login
     * Body: {username, password}
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        log.info("Admin login request: {}", request.get("username"));
        
        String username = request.get("username");
        String password = request.get("password");
        
        return adminService.authenticate(username, password);
    }

    /**
     * Admin logout
     * POST /api/admin/logout
     * Header: Authorization: Bearer {token}
     */
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            adminService.logout(token);
        }
        return Map.of("message", "Logged out successfully");
    }

    /**
     * Get debate history
     * GET /api/admin/debates?page=1&size=20&status=COMPLETED&userId=1
     */
    @GetMapping("/debates")
    public Map<String, Object> getDebateHistory(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        
        return adminService.getDebateHistory(page, size, status, userId);
    }

    /**
     * Get debate details
     * GET /api/admin/debates/{sessionId}
     */
    @GetMapping("/debates/{sessionId}")
    public Map<String, Object> getDebateDetails(@PathVariable Long sessionId) {
        // This would typically call a service method to get full debate details
        // For now, return basic info
        return Map.of(
                "sessionId", sessionId,
                "message", "Debate details endpoint - implementation pending"
        );
    }

    /**
     * Export debates
     * GET /api/admin/debates/export?status=COMPLETED&userId=1
     */
    @GetMapping("/debates/export")
    public ResponseEntity<String> exportDebates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        
        String csv = adminService.exportDebates(status, userId);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debates.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /**
     * Get user statistics
     * GET /api/admin/users/stats?userId=1
     */
    @GetMapping("/users/stats")
    public Map<String, Object> getUserStatistics(@RequestParam Long userId) {
        return adminService.getUserStatistics(userId);
    }

    /**
     * Get sensitive words
     * GET /api/admin/sensitive-words?page=1&size=20&category=profanity
     */
    @GetMapping("/sensitive-words")
    public Map<String, Object> getSensitiveWords(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String category) {
        
        return adminService.getSensitiveWords(page, size, category);
    }

    /**
     * Add sensitive word
     * POST /api/admin/sensitive-words
     * Body: {word, category, severity}
     */
    @PostMapping("/sensitive-words")
    public SensitiveWord addSensitiveWord(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        String word = request.get("word");
        String category = request.getOrDefault("category", "General");
        String severity = request.getOrDefault("severity", "MEDIUM");
        
        // Extract admin ID from token (simplified - in production, validate token properly)
        Long adminId = 1L; // Default admin
        
        return adminService.addSensitiveWord(word, category, severity, adminId);
    }

    /**
     * Delete sensitive word
     * DELETE /api/admin/sensitive-words/{wordId}
     */
    @DeleteMapping("/sensitive-words/{wordId}")
    public Map<String, String> deleteSensitiveWord(@PathVariable Long wordId) {
        adminService.deleteSensitiveWord(wordId);
        return Map.of("message", "Sensitive word deleted successfully");
    }

    /**
     * Get system configuration
     * GET /api/admin/config
     */
    @GetMapping("/config")
    public Map<String, Object> getSystemConfiguration() {
        return adminService.getSystemConfiguration();
    }

    /**
     * Update system configuration
     * PUT /api/admin/config
     * Body: {key: value, ...}
     */
    @PutMapping("/config")
    public Map<String, Object> updateSystemConfiguration(@RequestBody Map<String, Object> config) {
        // In a real implementation, this would update the system_configuration table
        return Map.of(
                "message", "Configuration update endpoint - implementation pending",
                "config", config
        );
    }
}
