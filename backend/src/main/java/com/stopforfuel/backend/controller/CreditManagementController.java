package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.CreditManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/credit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CreditManagementController {

    private final CreditManagementService creditManagementService;

    /**
     * GET /api/credit/overview
     * Returns all credit customers with aging breakdown and totals.
     */
    @GetMapping("/overview")
    public CreditManagementService.CreditOverview getOverview() {
        return creditManagementService.getCreditOverview();
    }

    /**
     * GET /api/credit/customer/{customerId}
     * Returns unpaid bills, outstanding statements, and recent payments for a customer.
     */
    @GetMapping("/customer/{customerId}")
    public CreditManagementService.CreditCustomerDetail getCustomerDetail(
            @PathVariable Long customerId) {
        return creditManagementService.getCustomerCreditDetail(customerId);
    }
}
