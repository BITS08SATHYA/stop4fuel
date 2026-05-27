package com.stopforfuel.backend.event;

import com.stopforfuel.backend.entity.ShiftClosingReport;
import com.stopforfuel.backend.repository.ShiftClosingReportRepository;
import com.stopforfuel.backend.service.ShiftClosingReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Auto-recomputes every non-FINALIZED ShiftClosingReport for the affected tenant
 * after a fuel price change commits, so cashiers don't see stale rates on open
 * shifts. FINALIZED reports are skipped — they're contractually frozen.
 *
 * Per-report try/catch so one broken report can't poison the whole recompute.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FuelPriceChangeRecomputeListener {

    private final ShiftClosingReportRepository reportRepository;
    private final ShiftClosingReportService reportService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFuelPriceChanged(FuelPriceChangedEvent event) {
        if (event.getScid() == null) {
            log.warn("FuelPriceChangedEvent missing scid — skipping auto-recompute");
            return;
        }
        List<ShiftClosingReport> targets =
                reportRepository.findByScidAndStatusNot(event.getScid(), "FINALIZED");
        if (targets.isEmpty()) return;

        int ok = 0;
        int failed = 0;
        for (ShiftClosingReport report : targets) {
            try {
                reportService.recomputeReportForScid(report.getId(), event.getScid(), "system:price-change");
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("Auto-recompute failed for report {} after fuel price change (productId={}): {}",
                        report.getId(), event.getProductId(), e.getMessage());
            }
        }
        log.info("Fuel price change for productId={} scid={} ({} → {}): recomputed {}/{} non-FINALIZED reports ({} failed)",
                event.getProductId(), event.getScid(), event.getPreviousPrice(), event.getNewPrice(),
                ok, targets.size(), failed);
    }
}
