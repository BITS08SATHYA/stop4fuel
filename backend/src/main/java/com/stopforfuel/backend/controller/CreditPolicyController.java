package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.CreditPolicy;
import com.stopforfuel.backend.service.CreditPolicyService;
import com.stopforfuel.config.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit/policies")
@RequiredArgsConstructor
public class CreditPolicyController {

    private final CreditPolicyService creditPolicyService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<CreditPolicy> getAllPolicies() {
        return creditPolicyService.getAllPolicies();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public CreditPolicy getPolicy(@PathVariable Long id) {
        return creditPolicyService.getPolicyById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_CREATE')")
    public CreditPolicy createPolicy(@Valid @RequestBody CreditPolicy policy) {
        if (policy.getScid() == null) {
            policy.setScid(SecurityUtils.getScid());
        }
        return creditPolicyService.createPolicy(policy);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public CreditPolicy updatePolicy(@PathVariable Long id, @Valid @RequestBody CreditPolicy policy) {
        return creditPolicyService.updatePolicy(id, policy);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_DELETE')")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        creditPolicyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
