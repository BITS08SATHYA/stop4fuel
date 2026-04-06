package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.BillSequence;
import com.stopforfuel.backend.enums.BillType;
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

    private static final Map<BillType, String> PREFIX_MAP = Map.of(
            BillType.CASH, "C",
            BillType.CREDIT, "A",
            BillType.STMT, "S"
    );

    @Transactional
    public String getNextBillNo(BillType billType) {
        // Statements use global sequential numbering: S-12186, S-12187, ...
        if (billType == BillType.STMT) {
            return getNextGlobalBillNo(billType, "S-");
        }

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

        String prefix = PREFIX_MAP.getOrDefault(billType, billType.name().substring(0, 1));
        return prefix + fyYear + "/" + seq.getLastNumber();
    }

    /**
     * Global sequential numbering (not FY-based). Uses fyYear=0 as a sentinel.
     */
    private String getNextGlobalBillNo(BillType billType, String prefix) {
        BillSequence seq = billSequenceRepository.findByTypeAndFyYear(billType, 0)
                .orElseGet(() -> {
                    BillSequence newSeq = new BillSequence();
                    newSeq.setType(billType);
                    newSeq.setFyYear(0);
                    newSeq.setLastNumber(0L);
                    return billSequenceRepository.save(newSeq);
                });

        seq.setLastNumber(seq.getLastNumber() + 1);
        billSequenceRepository.save(seq);

        return prefix + seq.getLastNumber();
    }

    private int getCurrentFyYear() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        // FY starts in April: Apr 2026 onwards = FY 2026-27 = 26
        return (month >= 4 ? year : year - 1) % 100;
    }
}
