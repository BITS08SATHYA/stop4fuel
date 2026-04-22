package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.bank.BankStatementRow;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BankStatementParserService {

    // Match anything that starts with a date (dd/mm/yy, dd-mm-yyyy, dd/mm/yyyy, etc.).
    private static final Pattern DATE_PREFIX = Pattern.compile("^(\\d{1,2}[-/.\\s](?:\\d{1,2}|[A-Za-z]{3})[-/.\\s]\\d{2,4})\\b");

    // Match monetary amounts: must have a decimal point with 2 digits after (Indian bank format convention).
    // This avoids false-positives on UTR/reference numbers like "UTR12345".
    private static final Pattern AMOUNT = Pattern.compile("([\\d,]+\\.\\d{2})(?:\\s*(Cr|Dr|CR|DR))?");

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    };

    public static class ParseResult {
        public final List<BankStatementRow> rows;
        public final int unparsedLineCount;
        public final List<String> warnings;
        public ParseResult(List<BankStatementRow> rows, int unparsedLineCount, List<String> warnings) {
            this.rows = rows;
            this.unparsedLineCount = unparsedLineCount;
            this.warnings = warnings;
        }
    }

    public ParseResult parse(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String rawText;
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            rawText = stripper.getText(doc);
        }
        return parseText(rawText);
    }

    ParseResult parseText(String rawText) {
        List<String> warnings = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            warnings.add("PDF contains no extractable text — likely a scanned image. OCR is not supported yet.");
            return new ParseResult(List.of(), 0, warnings);
        }

        String[] rawLines = rawText.split("\\r?\\n");

        // Stitch multi-line rows: a row starts when a line begins with a date; subsequent
        // non-date lines are folded into the previous row's narration until the next date line.
        List<String> stitched = new ArrayList<>();
        StringBuilder current = null;
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (DATE_PREFIX.matcher(trimmed).find()) {
                if (current != null) stitched.add(current.toString());
                current = new StringBuilder(trimmed);
            } else if (current != null) {
                current.append(' ').append(trimmed);
            }
            // lines before the first date-prefixed line (headers, account info) are skipped.
        }
        if (current != null) stitched.add(current.toString());

        List<BankStatementRow> rows = new ArrayList<>();
        int skipped = 0;
        int rowIdx = 0;
        for (String line : stitched) {
            BankStatementRow row = parseRow(line, rowIdx);
            if (row == null) {
                skipped++;
                continue;
            }
            rows.add(row);
            rowIdx++;
        }

        if (rows.isEmpty()) {
            warnings.add("Could not parse any transaction rows. The statement format may be unsupported.");
        } else if (skipped > 0) {
            warnings.add(skipped + " line(s) skipped — did not match the expected transaction format.");
        }

        return new ParseResult(rows, skipped, warnings);
    }

    private BankStatementRow parseRow(String line, int rowIdx) {
        Matcher dm = DATE_PREFIX.matcher(line);
        if (!dm.find()) return null;
        LocalDate txnDate = parseDate(dm.group(1).replace(" ", "-"));
        if (txnDate == null) return null;

        String afterDate = line.substring(dm.end()).trim();

        // Collect all amount-like tokens (must have decimal point + 2 decimals) with positions.
        List<AmountHit> hits = new ArrayList<>();
        Matcher am = AMOUNT.matcher(afterDate);
        int lastEnd = 0;
        while (am.find()) {
            String raw = am.group(1);
            String suffix = am.group(2);
            BigDecimal val = parseAmount(raw);
            if (val == null) continue;
            hits.add(new AmountHit(am.start(), val, suffix));
            lastEnd = am.end();
        }

        if (hits.isEmpty()) return null;

        // Last amount = balance. Before it: debit and/or credit.
        AmountHit balanceHit = hits.get(hits.size() - 1);
        BigDecimal balance = balanceHit.value;
        BigDecimal debit = null;
        BigDecimal credit = null;

        int narrationEnd = hits.get(0).start;
        String postAmounts = afterDate.substring(lastEnd).trim();
        List<AmountHit> txnHits = hits.subList(0, hits.size() - 1);

        if (txnHits.size() == 1) {
            AmountHit h = txnHits.get(0);
            String suffix = h.suffix == null ? "" : h.suffix.toUpperCase();
            if ("CR".equals(suffix)) credit = h.value;
            else if ("DR".equals(suffix)) debit = h.value;
            else {
                // Ambiguous single-amount row: if balance has a Cr/Dr suffix and went up vs prior, it's credit — but we don't
                // track running balance, so default to credit (customer-payment-in is the more useful match path).
                credit = h.value;
            }
        } else if (txnHits.size() >= 2) {
            // Assume column order [debit, credit] for most Indian banks (SBI, HDFC, ICICI).
            AmountHit d = txnHits.get(txnHits.size() - 2);
            AmountHit c = txnHits.get(txnHits.size() - 1);
            debit = d.value;
            credit = c.value;
        }

        String preAmounts = afterDate.substring(0, narrationEnd).trim();
        String narration = postAmounts.isEmpty() ? preAmounts : preAmounts + " " + postAmounts;
        narration = narration.replaceAll("\\s{2,}", " ").trim();

        BankStatementRow row = new BankStatementRow();
        row.setRowIndex(rowIdx);
        row.setTxnDate(txnDate);
        row.setNarration(narration);
        row.setDebit(debit);
        row.setCredit(credit);
        row.setBalance(balance);
        row.setRawLine(line);
        return row;
    }

    private LocalDate parseDate(String s) {
        String normalized = s.replace('.', '-').replace('/', '-').replace(' ', '-');
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(normalized, fmt);
            } catch (Exception ignored) {
                // try next
            }
            try {
                return LocalDate.parse(s, fmt);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class AmountHit {
        final int start;
        final BigDecimal value;
        final String suffix;
        AmountHit(int start, BigDecimal value, String suffix) {
            this.start = start;
            this.value = value;
            this.suffix = suffix;
        }
    }
}
