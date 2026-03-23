package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.CreditManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/credit")
@RequiredArgsConstructor
public class CreditManagementController {

    private final CreditManagementService creditManagementService;

    /**
     * GET /api/credit/overview
     * Returns all credit customers with aging breakdown and totals.
     */
    @GetMapping("/overview")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public CreditManagementService.CreditOverview getOverview(
            @RequestParam(required = false) String categoryType) {
        return creditManagementService.getCreditOverview(categoryType);
    }

    /**
     * GET /api/credit/customer/{customerId}
     * Returns unpaid bills, outstanding statements, and recent payments for a customer.
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public CreditManagementService.CreditCustomerDetail getCustomerDetail(
            @PathVariable Long customerId) {
        return creditManagementService.getCustomerCreditDetail(customerId);
    }
}
