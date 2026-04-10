package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.AwsBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final AwsBillingService awsBillingService;

    @GetMapping("/aws-mtd")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public AwsBillingService.BillingDto getMonthToDate() {
        return awsBillingService.getMonthToDate();
    }
}
