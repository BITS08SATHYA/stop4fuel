package com.stopforfuel.backend.listener;

import com.stopforfuel.backend.event.ShiftClosedEvent;
import com.stopforfuel.backend.service.StockNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fires only AFTER the shift-close transaction commits. {@code @Async} pushes the
 * SES/SNS/SSE work off the request thread so the cashier's close response stays fast,
 * and a rolled-back close never triggers notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockNotificationListener {

    private final StockNotificationService stockNotificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftClosed(ShiftClosedEvent event) {
        try {
            stockNotificationService.notifyShiftClose(event.shiftId());
        } catch (Exception e) {
            log.warn("Stock notify dispatch failed for shift {}: {}", event.shiftId(), e.getMessage());
        }
    }
}
