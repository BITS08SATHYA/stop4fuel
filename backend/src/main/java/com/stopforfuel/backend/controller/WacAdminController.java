package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.WeightedAverageCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/wac")
@RequiredArgsConstructor
public class WacAdminController {

    private final WeightedAverageCostService wacService;

    @PostMapping("/recompute")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<Long, BigDecimal>> recompute() {
        return ResponseEntity.ok(wacService.recomputeFromHistory());
    }
}
