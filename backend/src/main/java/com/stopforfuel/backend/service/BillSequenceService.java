package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.BillSequence;
import com.stopforfuel.backend.enums.BillType;
import com.stopforfuel.backend.repository.BillSequenceRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillSequenceService {

    private final BillSequenceRepository billSequenceRepository;
    private final StatementRepository statementRepository;

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

    /**
     * Read the next bill number that getNextBillNo would issue, without consuming it.
     * Lockless — won't block concurrent auto-gen. Returns "S-1" / "C-26/1" etc. when no
     * sequence row exists yet (first-ever issue on this tenant).
     */
    @Transactional(readOnly = true)
    public NextBillNoView peekNextBillNo(BillType billType) {
        boolean global = billType == BillType.STMT;
        int fyYear = global ? 0 : getCurrentFyYear();
        long lastNumber = billSequenceRepository.peekByTypeAndFyYear(billType, fyYear)
                .map(seq -> seq.getLastNumber())
                .orElse(0L);
        long next = lastNumber + 1;
        String prefix = PREFIX_MAP.getOrDefault(billType, billType.name().substring(0, 1));
        String formatted = global ? prefix + "-" + next : prefix + fyYear + "/" + next;
        Long highestInDb = (billType == BillType.STMT) ? statementRepository.findMaxNumericStatementNo() : null;
        return new NextBillNoView(lastNumber, next, formatted, highestInDb);
    }

    /**
     * Forward / rewind the sequence so the next consume call returns S-{nextNumber}.
     * Acquires the same pessimistic-write lock as the increment path so it serializes
     * with concurrent auto-gen runs. Forward-only by design — does NOT renumber any
     * existing rows.
     */
    @Transactional
    public NextBillNoView setNextBillNo(BillType billType, long nextNumber) {
        if (nextNumber < 1) {
            throw new IllegalArgumentException("nextNumber must be >= 1, got " + nextNumber);
        }
        boolean global = billType == BillType.STMT;
        int fyYear = global ? 0 : getCurrentFyYear();
        BillSequence seq = billSequenceRepository.findByTypeAndFyYear(billType, fyYear)
                .orElseGet(() -> {
                    BillSequence ns = new BillSequence();
                    ns.setType(billType);
                    ns.setFyYear(fyYear);
                    ns.setLastNumber(0L);
                    return ns;
                });
        seq.setLastNumber(nextNumber - 1);
        billSequenceRepository.save(seq);
        String prefix = PREFIX_MAP.getOrDefault(billType, billType.name().substring(0, 1));
        String formatted = global ? prefix + "-" + nextNumber : prefix + fyYear + "/" + nextNumber;
        Long highestInDb = (billType == BillType.STMT) ? statementRepository.findMaxNumericStatementNo() : null;
        return new NextBillNoView(nextNumber - 1, nextNumber, formatted, highestInDb);
    }

    /**
     * Lightweight view returned by peek/set. highestInDb is the MAX(numeric portion of statement_no)
     * for well-formed S-NNNNN rows in the statement table — only populated for BillType.STMT, null
     * for other types. Lets the gear-icon UI surface drift between the sequence counter and DB reality.
     */
    public record NextBillNoView(long lastNumber, long nextNumber, String nextBillNo, Long highestInDb) {}
}
