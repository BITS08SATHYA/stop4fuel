package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.AutoFixResultDTO;
import com.stopforfuel.backend.dto.BulkAutoFixResultDTO;
import com.stopforfuel.backend.dto.OrphanBillDTO;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.ShiftRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Admin recovery for the orphan-bill class of data anomalies surfaced on 2026-05-04.
 * Root cause + scope are captured in memory `project_orphan_bills_bug`.
 * <p>
 * autoFix mirrors the SQL transaction we ran 14 times that day:
 *   1. assign covering shift via [shift.start_time, shift.end_time] window over bill_date
 *   2. for Statement-party customers, look up the customer's covering DRAFT/NOT_PAID statement
 *      and link the bill, then recompute totals via StatementService.recomputeStatementTotals
 *   3. Local-party customers: only shift assignment, no statement work
 * <p>
 * Edge cases return a non-FIXED action with a reason — the UI surfaces a manual drawer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrphanBillService {

    /** Default cutoff: bills before this date are not surfaced unless includeHistorical=true.
     *  Picked to hide the 501 legacy MySQL-import bills (2017–2025). */
    private static final LocalDateTime DEFAULT_CUTOFF = LocalDateTime.of(2026, 1, 1, 0, 0);

    private final InvoiceBillRepository invoiceBillRepository;
    private final ShiftRepository shiftRepository;
    private final StatementRepository statementRepository;
    private final StatementService statementService;

    @Transactional(readOnly = true)
    public List<OrphanBillDTO> listOrphans(boolean includeHistorical) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime cutoff = includeHistorical ? LocalDateTime.of(1970, 1, 1, 0, 0) : DEFAULT_CUTOFF;
        List<InvoiceBill> bills = invoiceBillRepository.findOrphanBills(scid, cutoff);

        List<OrphanBillDTO> out = new ArrayList<>(bills.size());
        for (InvoiceBill bill : bills) {
            Long suggestedShiftId = bill.getShiftId() != null
                    ? null
                    : findCoveringShiftId(bill.getDate(), scid).orElse(null);

            Long suggestedStmtId = null;
            String suggestedStmtNo = null;
            if (isStatementCustomer(bill) && bill.getStatement() == null && bill.getDate() != null) {
                Optional<Statement> covering = findCoveringDraftStatement(
                        bill.getCustomer().getId(), scid, bill.getDate().toLocalDate());
                if (covering.isPresent()) {
                    suggestedStmtId = covering.get().getId();
                    suggestedStmtNo = covering.get().getStatementNo();
                }
            }
            out.add(OrphanBillDTO.from(bill, suggestedShiftId, suggestedStmtId, suggestedStmtNo));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public long countOrphans(boolean includeHistorical) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime cutoff = includeHistorical ? LocalDateTime.of(1970, 1, 1, 0, 0) : DEFAULT_CUTOFF;
        return invoiceBillRepository.findOrphanBills(scid, cutoff).size();
    }

    @Transactional
    public AutoFixResultDTO autoFix(Long billId) {
        Long scid = SecurityUtils.getScid();
        InvoiceBill bill = invoiceBillRepository.findByIdAndScid(billId, scid)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + billId));

        Long oldShiftId = bill.getShiftId();
        boolean nullShift = bill.getShiftId() == null;
        boolean isStatementCust = isStatementCustomer(bill);
        boolean noStatement = bill.getStatement() == null && isStatementCust && !bill.isIndependent();

        if (!nullShift && !noStatement) {
            return AutoFixResultDTO.builder()
                    .billId(billId).billNo(bill.getBillNo())
                    .action("NOOP").reason("Bill is no longer an orphan")
                    .oldShiftId(oldShiftId).newShiftId(oldShiftId)
                    .build();
        }

        // Step 1: assign covering shift if missing
        Long newShiftId = oldShiftId;
        if (nullShift) {
            if (bill.getDate() == null) {
                return AutoFixResultDTO.builder()
                        .billId(billId).billNo(bill.getBillNo())
                        .action("NEEDS_MANUAL_SHIFT").reason("Bill has no bill_date")
                        .oldShiftId(oldShiftId)
                        .build();
            }
            Optional<Long> covering = findCoveringShiftId(bill.getDate(), scid);
            if (covering.isEmpty()) {
                return AutoFixResultDTO.builder()
                        .billId(billId).billNo(bill.getBillNo())
                        .action("NEEDS_MANUAL_SHIFT")
                        .reason("No shift's [start_time, end_time] window covers " + bill.getDate())
                        .oldShiftId(oldShiftId)
                        .build();
            }
            newShiftId = covering.get();
            bill.setShiftId(newShiftId);
            invoiceBillRepository.save(bill);
        }

        // Step 2: for Statement customers, attach to covering statement and recompute
        if (noStatement && bill.getDate() != null) {
            Optional<Statement> covering = findCoveringDraftStatement(
                    bill.getCustomer().getId(), scid, bill.getDate().toLocalDate());
            if (covering.isEmpty()) {
                // System working as designed — bill waits for next auto-gen run.
                return AutoFixResultDTO.builder()
                        .billId(billId).billNo(bill.getBillNo())
                        .action("NO_STATEMENT_YET")
                        .reason("No DRAFT statement covers " + bill.getDate().toLocalDate()
                                + " yet. Bill will be picked up on next auto-gen.")
                        .oldShiftId(oldShiftId).newShiftId(newShiftId)
                        .build();
            }
            Statement target = covering.get();
            // Edge case: covering statement could be PAID (admin manually marked) — refuse.
            if ("PAID".equals(target.getStatus())) {
                return AutoFixResultDTO.builder()
                        .billId(billId).billNo(bill.getBillNo())
                        .action("NEEDS_MANUAL_STATEMENT")
                        .reason("Covering statement " + target.getStatementNo()
                                + " is PAID. Un-finalize first or attach manually.")
                        .oldShiftId(oldShiftId).newShiftId(newShiftId)
                        .build();
            }
            bill.setStatement(target);
            invoiceBillRepository.save(bill);

            // Recompute that statement's totals + quantity from currently-linked bills
            statementService.recomputeStatementTotals(target);
            statementRepository.save(target);

            return AutoFixResultDTO.builder()
                    .billId(billId).billNo(bill.getBillNo())
                    .action("FIXED")
                    .oldShiftId(oldShiftId).newShiftId(newShiftId)
                    .statementLinkedId(target.getId()).statementLinkedNo(target.getStatementNo())
                    .build();
        }

        // Local customer or independent — only the shift was set.
        return AutoFixResultDTO.builder()
                .billId(billId).billNo(bill.getBillNo())
                .action(isStatementCust ? "FIXED" : "SHIFT_ASSIGNED_LOCAL")
                .oldShiftId(oldShiftId).newShiftId(newShiftId)
                .build();
    }

    @Transactional
    public BulkAutoFixResultDTO autoFixAll(boolean includeHistorical) {
        List<OrphanBillDTO> snapshot = listOrphans(includeHistorical);
        List<AutoFixResultDTO> results = new ArrayList<>(snapshot.size());
        int fixed = 0, needsManual = 0, waitingForStatement = 0;
        for (OrphanBillDTO row : snapshot) {
            try {
                AutoFixResultDTO r = autoFix(row.getId());
                results.add(r);
                switch (r.getAction()) {
                    case "FIXED", "SHIFT_ASSIGNED_LOCAL" -> fixed++;
                    case "NEEDS_MANUAL_SHIFT", "NEEDS_MANUAL_STATEMENT" -> needsManual++;
                    case "NO_STATEMENT_YET" -> waitingForStatement++;
                    default -> { /* NOOP */ }
                }
            } catch (Exception e) {
                log.error("autoFixAll failed for bill {}: {}", row.getId(), e.getMessage());
                results.add(AutoFixResultDTO.builder()
                        .billId(row.getId()).billNo(row.getBillNo())
                        .action("ERROR").reason(e.getMessage())
                        .build());
                needsManual++;
            }
        }
        return BulkAutoFixResultDTO.builder()
                .total(snapshot.size())
                .fixed(fixed).needsManual(needsManual).waitingForStatement(waitingForStatement)
                .results(results)
                .build();
    }

    // ---- helpers ----

    private Optional<Long> findCoveringShiftId(LocalDateTime when, Long scid) {
        if (when == null) return Optional.empty();
        List<Shift> covering = shiftRepository.findCoveringShifts(scid, when, PageRequest.of(0, 1));
        return covering.isEmpty() ? Optional.empty() : Optional.of(covering.get(0).getId());
    }

    private Optional<Statement> findCoveringDraftStatement(Long customerId, Long scid, LocalDate date) {
        List<Statement> matches = statementRepository.findCoveringStatements(customerId, scid, date);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    private boolean isStatementCustomer(InvoiceBill bill) {
        if (bill.getCustomer() == null || bill.getCustomer().getParty() == null) return false;
        try {
            return "Statement".equalsIgnoreCase(bill.getCustomer().getParty().getPartyType());
        } catch (org.hibernate.LazyInitializationException ignored) {
            return false;
        }
    }
}
