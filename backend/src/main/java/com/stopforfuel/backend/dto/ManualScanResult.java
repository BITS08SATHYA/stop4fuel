package com.stopforfuel.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result payload for the manual invoice-check scan. Surfaces per-customer outcomes
 * so the UI can list which customers were blocked, which passed, and which were
 * skipped (policy disabled or no credit limit set).
 */
@Data
public class ManualScanResult {
    private int scannedCount;
    private int blockedCount;
    private int unblockedCount;
    private int skippedCount;
    private List<ScanEntry> entries = new ArrayList<>();

    @Data
    public static class ScanEntry {
        private Long customerId;
        private String customerName;
        private String partyType;           // "Local" / "Statement" / null
        private String outcome;             // "BLOCKED" | "UNBLOCKED" | "PASS" | "SKIPPED"
        private String reason;              // blocking/unblocking reason, or skip reason
        private BigDecimal utilizationPercent; // null when not computed
        private Long oldestUnpaidDays;      // null when no unpaid bill
    }
}
