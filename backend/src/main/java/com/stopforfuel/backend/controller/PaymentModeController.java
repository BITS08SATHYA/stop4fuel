package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.PaymentMode;
import com.stopforfuel.backend.repository.PaymentModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-modes")
@RequiredArgsConstructor
public class PaymentModeController {

    private final PaymentModeRepository paymentModeRepository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<PaymentMode> getAll() {
        return paymentModeRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public PaymentMode getById(@PathVariable Long id) {
        return paymentModeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment mode not found"));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_MANAGE')")
    public PaymentMode create(@Valid @RequestBody PaymentMode paymentMode) {
        return paymentModeRepository.save(paymentMode);
    }
}
