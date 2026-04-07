package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.PaytmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/paytm")
@RequiredArgsConstructor
public class PaytmController {

    private final PaytmService paytmService;

    /**
     * Initiate a payment request to the Paytm POS terminal.
     */
    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(String.valueOf(request.get("amount")));
        Long invoiceBillId = request.get("invoiceBillId") != null
                ? Long.valueOf(String.valueOf(request.get("invoiceBillId"))) : null;
        Long statementId = request.get("statementId") != null
                ? Long.valueOf(String.valueOf(request.get("statementId"))) : null;
        String txnType = String.valueOf(request.getOrDefault("txnType", "CASH_INVOICE"));

        Map<String, Object> result = paytmService.initiatePayment(amount, invoiceBillId, statementId, txnType);
        return ResponseEntity.ok(result);
    }

    /**
     * Callback endpoint for Paytm to send transaction results.
     * This endpoint is public (no auth) — security is via checksum verification.
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestParam Map<String, String> params) {
        Map<String, Object> result = paytmService.handleCallback(params);
        return ResponseEntity.ok(result);
    }

    /**
     * Check the status of a Paytm transaction (frontend polls this).
     */
    @GetMapping("/status/{merchantTxnId}")
    public ResponseEntity<Map<String, Object>> checkStatus(@PathVariable String merchantTxnId) {
        Map<String, Object> result = paytmService.checkStatus(merchantTxnId);
        return ResponseEntity.ok(result);
    }
}
