package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.bank.BankMatchCandidate;
import com.stopforfuel.backend.dto.bank.BankStatementRow;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BankStatementMatcherService {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01"); // ±1%
    private static final int MAX_MATCHES_PER_ROW = 5;

    // Tokens that are noise in bank narrations and should not contribute to name matching.
    private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "UPI", "NEFT", "IMPS", "RTGS", "TRF", "TRANSFER", "PAYMENT", "PAY", "PYMT",
            "CREDIT", "CR", "DEBIT", "DR", "REF", "FROM", "TO", "BY", "FOR",
            "INB", "ACH", "NACH", "CHQ", "CHEQUE", "CASH", "DEPOSIT", "WITHDRAWAL",
            "BANK", "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "PNB", "FED"
    ));

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^A-Za-z0-9]+");

    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;

    public BankStatementMatcherService(InvoiceBillRepository invoiceBillRepository,
                                       StatementRepository statementRepository) {
        this.invoiceBillRepository = invoiceBillRepository;
        this.statementRepository = statementRepository;
    }

    public List<BankMatchCandidate> matchRow(BankStatementRow row, Long scid) {
        if (row.getCredit() == null || row.getCredit().signum() <= 0) return List.of();
        BigDecimal amount = row.getCredit();
        BigDecimal delta = amount.multiply(AMOUNT_TOLERANCE).setScale(4, RoundingMode.HALF_UP);
        BigDecimal low = amount.subtract(delta);
        BigDecimal high = amount.add(delta);

        Set<String> narrationTokens = tokenize(row.getNarration());
        List<BankMatchCandidate> out = new ArrayList<>();

        List<InvoiceBill> invoices = invoiceBillRepository.findOutstandingCreditByAmountRange(low, high, scid);
        for (InvoiceBill ib : invoices) {
            String custName = ib.getCustomer() != null ? ib.getCustomer().getName() : null;
            int nameScore = nameScore(narrationTokens, custName);
            int amountScore = exactAmountScore(amount, ib.getNetAmount());
            int total = amountScore + nameScore * 10;
            BankMatchCandidate c = new BankMatchCandidate();
            c.setType("INVOICE");
            c.setId(ib.getId());
            c.setDisplayNo(ib.getBillNo());
            c.setCustomerId(ib.getCustomer() != null ? ib.getCustomer().getId() : null);
            c.setCustomerName(custName);
            c.setAmount(ib.getNetAmount());
            c.setDocDate(ib.getDate() != null ? ib.getDate().toLocalDate() : null);
            c.setMatchReason(nameScore > 0 ? "amount+name" : "amount");
            c.setScore(total);
            out.add(c);
        }

        List<Statement> statements = statementRepository.findOutstandingByBalanceRange(low, high, scid);
        for (Statement s : statements) {
            String custName = s.getCustomer() != null ? s.getCustomer().getName() : null;
            int nameScore = nameScore(narrationTokens, custName);
            int amountScore = exactAmountScore(amount, s.getBalanceAmount());
            int total = amountScore + nameScore * 10;
            BankMatchCandidate c = new BankMatchCandidate();
            c.setType("STATEMENT");
            c.setId(s.getId());
            c.setDisplayNo(s.getStatementNo());
            c.setCustomerId(s.getCustomer() != null ? s.getCustomer().getId() : null);
            c.setCustomerName(custName);
            c.setAmount(s.getBalanceAmount());
            c.setDocDate(s.getStatementDate());
            c.setMatchReason(nameScore > 0 ? "amount+name" : "amount");
            c.setScore(total);
            out.add(c);
        }

        out.sort(Comparator.comparingInt(BankMatchCandidate::getScore).reversed());
        if (out.size() > MAX_MATCHES_PER_ROW) return new ArrayList<>(out.subList(0, MAX_MATCHES_PER_ROW));
        return out;
    }

    private int exactAmountScore(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return 0;
        return a.compareTo(b) == 0 ? 5 : 1;
    }

    private int nameScore(Set<String> narrationTokens, String customerName) {
        if (narrationTokens.isEmpty() || customerName == null || customerName.isBlank()) return 0;
        Set<String> custTokens = tokenize(customerName);
        if (custTokens.isEmpty()) return 0;
        int overlap = 0;
        for (String t : custTokens) {
            if (narrationTokens.contains(t)) overlap++;
        }
        return overlap;
    }

    static Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        Set<String> out = new HashSet<>();
        for (String raw : TOKEN_SPLITTER.split(text.toUpperCase())) {
            if (raw.length() < 3) continue;
            if (NOISE_TOKENS.contains(raw)) continue;
            if (raw.chars().allMatch(Character::isDigit)) continue;
            out.add(raw);
        }
        return out;
    }
}
