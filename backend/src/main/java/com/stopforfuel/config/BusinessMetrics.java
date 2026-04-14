package com.stopforfuel.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final MeterRegistry registry;
    public final Timer shiftCloseDuration;
    public final Timer aiRequestDuration;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.shiftCloseDuration = Timer.builder("shift.close.duration")
                .description("Time taken to close a shift (persist + finalize + report)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.aiRequestDuration = Timer.builder("ai.request.duration")
                .description("Time taken by Spring AI calls to Anthropic")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void invoiceCreated(String billType) {
        Counter.builder("invoices.created")
                .tag("bill_type", billType == null ? "unknown" : billType)
                .register(registry)
                .increment();
    }

    public void paymentRecorded(String paymentMode, String paidAgainst) {
        Counter.builder("payments.recorded")
                .tag("payment_mode", paymentMode == null ? "unknown" : paymentMode)
                .tag("paid_against", paidAgainst)
                .register(registry)
                .increment();
    }

    public void shiftOpened() {
        Counter.builder("shifts.opened").register(registry).increment();
    }

    public void shiftClosed() {
        Counter.builder("shifts.closed").register(registry).increment();
    }
}
