package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.BillSequence;
import com.stopforfuel.backend.repository.BillSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillSequenceService {

    private final BillSequenceRepository billSequenceRepository;

    private static final Map<String, String> PREFIX_MAP = Map.of(
            "CASH", "C",
            "CREDIT", "A",
            "STMT", "S"
    );

    @Transactional
    public String getNextBillNo(String billType) {
        int fyYear = getCurrentFyYear();

        BillSequence seq = billSequenceRepository.findByTypeAndFyYear(billType, fyYear)
                .orElseGet(() -> {
                    BillSequence newSeq = new BillSequence();
                    newSeq.setType(billType);
                    newSeq.setFyYear(fyYear);
                    newSeq.setLastNumber(0L);
                    return billSequenceRepository.save(newSeq);
                });

        seq.setLastNumber(seq.getLastNumber() + 1);
        billSequenceRepository.save(seq);

        String prefix = PREFIX_MAP.getOrDefault(billType, billType.substring(0, 1));
        return prefix + fyYear + "/" + seq.getLastNumber();
    }

    private int getCurrentFyYear() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        // FY starts in April: Apr 2026 onwards = FY 2026-27 = 26
        return (month >= 4 ? year : year - 1) % 100;
    }
}
