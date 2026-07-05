package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.ProductAnalyticsDTO;
import com.stopforfuel.backend.dto.TankAnalyticsDTO;
import com.stopforfuel.backend.service.ProductAnalyticsService;
import com.stopforfuel.backend.service.TankAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ProductAnalyticsService productAnalyticsService;
    private final TankAnalyticsService tankAnalyticsService;

    @GetMapping("/products")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ProductAnalyticsDTO getProductAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return productAnalyticsService.getProductAnalytics(fromDate, toDate);
    }

    @GetMapping("/tanks")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public TankAnalyticsDTO getTankAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "2") int leadTimeDays,
            @RequestParam(defaultValue = "12000") double tankerLoadLiters) {
        return tankAnalyticsService.getTankAnalytics(fromDate, toDate, leadTimeDays, tankerLoadLiters);
    }
}
