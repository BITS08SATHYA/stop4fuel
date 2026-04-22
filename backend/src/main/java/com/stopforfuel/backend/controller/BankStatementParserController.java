package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.bank.BankMatchCandidate;
import com.stopforfuel.backend.dto.bank.BankStatementParseResponse;
import com.stopforfuel.backend.dto.bank.BankStatementRow;
import com.stopforfuel.backend.service.BankStatementMatcherService;
import com.stopforfuel.backend.service.BankStatementParserService;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/bank-statements")
public class BankStatementParserController {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final BankStatementParserService parserService;
    private final BankStatementMatcherService matcherService;

    public BankStatementParserController(BankStatementParserService parserService,
                                         BankStatementMatcherService matcherService) {
        this.parserService = parserService;
        this.matcherService = matcherService;
    }

    @PostMapping(path = "/parse", consumes = "multipart/form-data")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<BankStatementParseResponse> parse(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String customerNameContains) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            BankStatementParseResponse err = new BankStatementParseResponse();
            err.getWarnings().add("File exceeds the 10 MB limit. Split the statement into smaller date ranges and retry.");
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(err);
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".pdf")) {
            BankStatementParseResponse err = new BankStatementParseResponse();
            err.getWarnings().add("Only PDF files are supported.");
            return ResponseEntity.badRequest().body(err);
        }

        Long scid = SecurityUtils.getScid();
        BankStatementParserService.ParseResult parsed = parserService.parse(file);

        BankStatementParseResponse resp = new BankStatementParseResponse();
        resp.getWarnings().addAll(parsed.warnings);
        resp.setUnparsedLineCount(parsed.unparsedLineCount);

        String nameFilter = customerNameContains == null ? null : customerNameContains.trim().toLowerCase();
        boolean hasNameFilter = nameFilter != null && !nameFilter.isEmpty();

        List<BankStatementRow> filteredRows = new ArrayList<>();
        int keptIdx = 0;
        for (BankStatementRow row : parsed.rows) {
            if (minAmount != null && !amountInRange(row, minAmount, null)) continue;
            if (maxAmount != null && !amountInRange(row, null, maxAmount)) continue;

            List<BankMatchCandidate> matches = matcherService.matchRow(row, scid);

            if (hasNameFilter) {
                boolean narrationHit = row.getNarration() != null
                        && row.getNarration().toLowerCase().contains(nameFilter);
                boolean matchHit = matches.stream().anyMatch(m ->
                        m.getCustomerName() != null && m.getCustomerName().toLowerCase().contains(nameFilter));
                if (!narrationHit && !matchHit) continue;
            }

            row.setRowIndex(keptIdx);
            filteredRows.add(row);
            if (!matches.isEmpty()) {
                resp.getMatchesByRow().put(keptIdx, matches);
            }
            keptIdx++;
        }
        resp.setRows(filteredRows);
        return ResponseEntity.ok(resp);
    }

    private boolean amountInRange(BankStatementRow row, BigDecimal min, BigDecimal max) {
        BigDecimal[] candidates = new BigDecimal[]{row.getCredit(), row.getDebit()};
        for (BigDecimal v : candidates) {
            if (v == null) continue;
            if (min != null && v.compareTo(min) < 0) continue;
            if (max != null && v.compareTo(max) > 0) continue;
            return true;
        }
        return false;
    }
}
