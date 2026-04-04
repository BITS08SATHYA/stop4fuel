package com.stopforfuel.backend.config;

import com.stopforfuel.backend.entity.PaymentMode;
import com.stopforfuel.backend.repository.PaymentModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PaymentModeRepository paymentModeRepository;

    @Override
    public void run(String... args) {
        seedPaymentModes();
    }

    private void seedPaymentModes() {
        List<String> modes = List.of("CASH", "CARD", "CHEQUE", "UPI", "CCMS", "BANK_TRANSFER", "NEFT");
        for (String mode : modes) {
            if (paymentModeRepository.findByModeName(mode).isEmpty()) {
                PaymentMode pm = new PaymentMode();
                pm.setModeName(mode);
                paymentModeRepository.save(pm);
            }
        }
    }
}
