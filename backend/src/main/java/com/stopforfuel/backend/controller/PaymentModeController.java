package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.PaymentMode;
import com.stopforfuel.backend.repository.PaymentModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-modes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentModeController {

    private final PaymentModeRepository paymentModeRepository;

    @GetMapping
    public List<PaymentMode> getAll() {
        return paymentModeRepository.findAll();
    }

    @GetMapping("/{id}")
    public PaymentMode getById(@PathVariable Long id) {
        return paymentModeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment mode not found"));
    }

    @PostMapping
    public PaymentMode create(@RequestBody PaymentMode paymentMode) {
        return paymentModeRepository.save(paymentMode);
    }
}
