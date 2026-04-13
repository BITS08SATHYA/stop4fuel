package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.AiAnalyticsService;
import com.stopforfuel.backend.service.AiAnalyticsService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-analytics")
@RequiredArgsConstructor
public class AiAnalyticsController {

    private final AiAnalyticsService aiAnalyticsService;

    @PostMapping("/chat")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return aiAnalyticsService.chat(request.message(), request.history());
    }

    @GetMapping("/insights")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public InsightsResponse getInsights() {
        return aiAnalyticsService.generateInsights();
    }

    public record ChatRequest(String message, List<ChatMessageDTO> history) {}
}
