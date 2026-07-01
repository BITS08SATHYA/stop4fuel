package com.stopforfuel.backend.event;

import com.stopforfuel.backend.repository.ShiftClosingReportRepository;
import com.stopforfuel.backend.service.ShiftClosingReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recomputes the affected shifts' closing reports AFTER an invoice edit/move commits, so cached
 * cash/credit totals stay correct. Runs after commit (not inline) and per-shift try/catch so a
 * single broken shift report can never roll back or fail the invoice write that triggered it —
 * the report is left as-is and the failure logged. Skips FINALIZED reports.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvoiceShiftRecomputeListener {

    private final ShiftClosingReportRepository reportRepository;
    private final ShiftClosingReportService reportService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecomputeRequested(InvoiceShiftRecomputeEvent event) {
        if (event.getScid() == null) return;
        for (Long shiftId : event.getShiftIds()) {
            if (shiftId == null) continue;
            try {
                reportRepository.findByShift_Id(shiftId).ifPresent(report -> {
                    if (!"FINALIZED".equals(report.getStatus())) {
                        reportService.recomputeReportForScid(report.getId(), event.getScid(), event.getReason());
                    }
                });
            } catch (Exception e) {
                log.warn("Post-commit report recompute failed for shift {} ({}) — report left stale: {}",
                        shiftId, event.getReason(), e.toString());
            }
        }
    }
}
