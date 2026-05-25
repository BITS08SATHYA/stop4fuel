package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.DayWiseStatementPreview;
import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.dto.StatementStats;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.ReportLayout;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatementService {

    private final StatementRepository statementRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final PaymentRepository paymentRepository;
    private final BillSequenceService billSequenceService;
    private final StatementPdfGenerator pdfGenerator;
    private final S3StorageService s3StorageService;

    @Transactional(readOnly = true)
    public Page<Statement> getStatements(Long customerId, String status, String categoryType, LocalDate fromDate, LocalDate toDate, String search, Pageable pageable) {
        String ct = (categoryType != null && !categoryType.isEmpty()) ? categoryType : null;
        Long scid = SecurityUtils.getScid();
        if (search != null && !search.isBlank()) {
            return statementRepository.findWithFiltersAndSearch(customerId, status, ct, fromDate, toDate, search.trim(), scid, pageable);
        }
        return statementRepository.findWithFilters(customerId, status, ct, fromDate, toDate, scid, pageable);
    }

    @Transactional(readOnly = true)
    public List<Statement> getAllStatements() {
        return statementRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public Statement getStatementById(Long id) {
        return statementRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Statement getStatementByNo(String statementNo) {
        return statementRepository.findByStatementNo(statementNo)
                .orElseThrow(() -> new RuntimeException("Statement not found with no: " + statementNo));
    }

    @Transactional(readOnly = true)
    public List<Statement> getStatementsByCustomer(Long customerId) {
        return statementRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Statement> getOutstandingStatements() {
        return statementRepository.findByStatusAndScid("NOT_PAID", SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Statement> getOutstandingByCustomer(Long customerId) {
        return statementRepository.findByCustomerIdAndStatus(customerId, "NOT_PAID");
    }

    /**
     * Generate a statement for a customer for the given date range.
     * Supports optional filters: vehicleId, productId, or specific billIds.
     * reportLayout is optional — defaults to VEHICLE_WISE (existing behavior).
     */
    @Transactional
    public Statement generateStatement(Long customerId, LocalDate fromDate, LocalDate toDate,
                                       Long vehicleId, Long productId, List<Long> billIds,
                                       ReportLayout reportLayout) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        List<InvoiceBill> bills;

        if (billIds != null && !billIds.isEmpty()) {
            // Bill-wise: user selected specific bills
            bills = invoiceBillRepository.findUnlinkedCreditBillsByIds(billIds, SecurityUtils.getScid());
            // Validate all bills belong to this customer
            for (InvoiceBill bill : bills) {
                if (!bill.getCustomer().getId().equals(customerId)) {
                    throw new BusinessException("Bill " + bill.getId() + " does not belong to customer " + customerId);
                }
            }
        } else {
            // Convert dates to LocalDateTime for query (full day range)
            LocalDateTime fromDateTime = fromDate.atStartOfDay();
            LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

            if (vehicleId != null && productId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByVehicleAndProduct(
                        customerId, fromDateTime, toDateTime, vehicleId, productId);
            } else if (vehicleId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByVehicle(
                        customerId, fromDateTime, toDateTime, vehicleId);
            } else if (productId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByProduct(
                        customerId, fromDateTime, toDateTime, productId);
            } else {
                bills = invoiceBillRepository.findUnlinkedCreditBills(
                        customerId, fromDateTime, toDateTime);
            }
        }

        if (bills.isEmpty()) {
            throw new BusinessException("No unlinked credit bills found for the selected filters");
        }

        return createStatementFromBills(customer, bills, fromDate, toDate, reportLayout);
    }

    /**
     * Back-compat overload — defaults to VEHICLE_WISE layout.
     */
    @Transactional
    public Statement generateStatement(Long customerId, LocalDate fromDate, LocalDate toDate,
                                       Long vehicleId, Long productId, List<Long> billIds) {
        return generateStatement(customerId, fromDate, toDate, vehicleId, productId, billIds, ReportLayout.VEHICLE_WISE);
    }

    /**
     * Shared helper used by single-generate and batch-generate. Persists a Statement
     * for the given customer + bill set, consumes a statement-sequence number, and links
     * the bills to the new statement. Caller is responsible for resolving + validating
     * the bill list before passing it in.
     */
    private Statement createStatementFromBills(Customer customer, List<InvoiceBill> bills,
                                               LocalDate fromDate, LocalDate toDate,
                                               ReportLayout reportLayout) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill bill : bills) {
            if (bill.getNetAmount() != null) {
                totalAmount = totalAmount.add(bill.getNetAmount());
            }
        }
        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);

        String nextStatementNo = billSequenceService.getNextBillNo(com.stopforfuel.backend.enums.BillType.STMT);

        Statement statement = new Statement();
        statement.setScid(customer.getScid());
        statement.setStatementNo(nextStatementNo);
        statement.setCustomer(customer);
        statement.setFromDate(fromDate);
        statement.setToDate(toDate);
        statement.setStatementDate(LocalDate.now());
        statement.setNumberOfBills(bills.size());
        statement.setTotalAmount(totalAmount);
        statement.setRoundingAmount(roundingAmount);
        statement.setNetAmount(netAmount);
        statement.setReceivedAmount(BigDecimal.ZERO);
        statement.setBalanceAmount(netAmount);
        statement.setStatus("NOT_PAID");
        statement.setReportLayout(reportLayout != null ? reportLayout : ReportLayout.VEHICLE_WISE);

        List<Long> invoiceBillIds = bills.stream().map(InvoiceBill::getId).toList();
        statement.setTotalQuantity(invoiceBillRepository.sumQuantityByBillIds(invoiceBillIds));

        Statement saved = statementRepository.save(statement);
        for (InvoiceBill bill : bills) {
            bill.setStatement(saved);
            invoiceBillRepository.save(bill);
        }
        return saved;
    }

    /**
     * Regenerate an existing statement in-place, keeping the same statement number.
     * Unlinks old bills, fetches new bills based on updated filters, recalculates totals.
     */
    @Transactional
    public Statement regenerateStatement(Long statementId, LocalDate fromDate, LocalDate toDate,
                                         Long vehicleId, Long productId, List<Long> billIds, Long newCustomerId) {
        return regenerateStatement(statementId, fromDate, toDate, vehicleId, productId, billIds, newCustomerId, null);
    }

    @Transactional
    public Statement regenerateStatement(Long statementId, LocalDate fromDate, LocalDate toDate,
                                         Long vehicleId, Long productId, List<Long> billIds, Long newCustomerId,
                                         ReportLayout reportLayout) {
        Statement statement = statementRepository.findByIdForUpdate(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + statementId));

        if (statement.getReceivedAmount() != null && statement.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Cannot regenerate a statement that has received payments");
        }

        // If a new customer is specified, update the statement's customer
        if (newCustomerId != null && !newCustomerId.equals(statement.getCustomer().getId())) {
            Customer newCustomer = customerRepository.findById(newCustomerId)
                    .orElseThrow(() -> new RuntimeException("Customer not found with id: " + newCustomerId));
            statement.setCustomer(newCustomer);
        }
        if (reportLayout != null) {
            statement.setReportLayout(reportLayout);
        }

        Long customerId = statement.getCustomer().getId();

        // Unlink all currently linked bills
        List<InvoiceBill> oldBills = invoiceBillRepository.findByStatementId(statementId);
        for (InvoiceBill bill : oldBills) {
            bill.setStatement(null);
            invoiceBillRepository.save(bill);
        }

        // Fetch new bills based on updated parameters
        List<InvoiceBill> newBills;
        if (billIds != null && !billIds.isEmpty()) {
            newBills = invoiceBillRepository.findUnlinkedCreditBillsByIds(billIds, SecurityUtils.getScid());
            for (InvoiceBill bill : newBills) {
                if (!bill.getCustomer().getId().equals(customerId)) {
                    throw new BusinessException("Bill " + bill.getId() + " does not belong to customer " + customerId);
                }
            }
        } else {
            LocalDateTime fromDateTime = fromDate.atStartOfDay();
            LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

            if (vehicleId != null && productId != null) {
                newBills = invoiceBillRepository.findUnlinkedCreditBillsByVehicleAndProduct(
                        customerId, fromDateTime, toDateTime, vehicleId, productId);
            } else if (vehicleId != null) {
                newBills = invoiceBillRepository.findUnlinkedCreditBillsByVehicle(
                        customerId, fromDateTime, toDateTime, vehicleId);
            } else if (productId != null) {
                newBills = invoiceBillRepository.findUnlinkedCreditBillsByProduct(
                        customerId, fromDateTime, toDateTime, productId);
            } else {
                newBills = invoiceBillRepository.findUnlinkedCreditBills(
                        customerId, fromDateTime, toDateTime);
            }
        }

        if (newBills.isEmpty()) {
            throw new BusinessException("No unlinked credit bills found for the selected filters");
        }

        // Update statement in-place (keep statementNo). Regenerate semantics: received resets,
        // status flips back to NOT_PAID. Totals come from recomputeStatementTotals after re-link.
        statement.setFromDate(fromDate);
        statement.setToDate(toDate);
        statement.setStatementDate(LocalDate.now());
        statement.setReceivedAmount(BigDecimal.ZERO);
        statement.setStatus("NOT_PAID");

        // Persist intermediate state, link new bills, then recompute totals from DB.
        Statement saved = statementRepository.save(statement);
        for (InvoiceBill bill : newBills) {
            bill.setStatement(saved);
            invoiceBillRepository.save(bill);
        }
        recomputeStatementTotals(saved);
        saved = statementRepository.save(saved);

        // Auto-generate PDF with updated data
        generateAndStorePdf(saved.getId());

        return saved;
    }

    /**
     * Preview unlinked credit bills grouped by calendar day, with running totals and
     * suggested split boundaries that keep each statement's net amount under maxAmount.
     * Each day stays intact in one statement (the algorithm closes BEFORE adding a day
     * that would overflow). A single day whose own total exceeds the cap is kept as
     * its own statement and flagged with exceedsCap=true so the UI can warn the user.
     *
     * Returns the full day-grouped view so the UI can render running totals and let the
     * user drag split boundaries. The suggestedSplits array carries billIds per group —
     * the UI submits the user's adjusted boundaries back as billIds to generateBatch.
     */
    @Transactional(readOnly = true)
    public DayWiseStatementPreview previewDayWise(Long customerId, LocalDate fromDate, LocalDate toDate,
                                                  BigDecimal maxAmount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);
        // Use bill.date windowing (not shift-based) — the user picked calendar days
        // and expects bills on those dates, not bills from shifts that opened on those dates.
        List<InvoiceBill> bills = invoiceBillRepository.findUnlinkedCreditBillsByBillDate(customerId, fromDateTime, toDateTime);

        // Reject upfront — day-wise grouping requires a bill date.
        for (InvoiceBill bill : bills) {
            if (bill.getDate() == null) {
                throw new BusinessException("Bill " + bill.getId() + " has no date — cannot group day-wise");
            }
        }

        // Group by calendar day (ascending).
        TreeMap<LocalDate, List<InvoiceBill>> byDate = new TreeMap<>();
        for (InvoiceBill bill : bills) {
            LocalDate d = bill.getDate().toLocalDate();
            byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(bill);
        }

        BigDecimal grandTotalRaw = BigDecimal.ZERO;
        BigDecimal runningRaw = BigDecimal.ZERO;
        List<DayWiseStatementPreview.DayBucket> days = new ArrayList<>(byDate.size());
        for (Map.Entry<LocalDate, List<InvoiceBill>> e : byDate.entrySet()) {
            List<InvoiceBill> dayBills = e.getValue();
            BigDecimal dayTotalRaw = BigDecimal.ZERO;
            for (InvoiceBill b : dayBills) {
                if (b.getNetAmount() != null) dayTotalRaw = dayTotalRaw.add(b.getNetAmount());
            }
            runningRaw = runningRaw.add(dayTotalRaw);
            grandTotalRaw = grandTotalRaw.add(dayTotalRaw);
            days.add(DayWiseStatementPreview.DayBucket.builder()
                    .date(e.getKey())
                    .billCount(dayBills.size())
                    .dayTotal(dayTotalRaw)
                    .cumulativeTotal(runningRaw.setScale(0, RoundingMode.HALF_UP))
                    .bills(dayBills.stream().map(InvoiceBillDTO::from).toList())
                    .build());
        }

        // Split algorithm — chronological, days stay intact.
        List<DayWiseStatementPreview.SplitGroup> splits = new ArrayList<>();
        if (!byDate.isEmpty()) {
            BigDecimal cap = maxAmount != null ? maxAmount.setScale(0, RoundingMode.HALF_UP) : null;
            log.info("[previewDayWise] customer={} from={} to={} cap={} days={} grandTotal={}",
                    customerId, fromDate, toDate, cap, byDate.size(), grandTotalRaw);

            List<Long> currentBillIds = new ArrayList<>();
            BigDecimal currentRaw = BigDecimal.ZERO;
            int currentDayCount = 0;
            LocalDate currentFrom = null;
            LocalDate currentTo = null;
            boolean currentExceeds = false;
            int splitIndex = 0;

            for (Map.Entry<LocalDate, List<InvoiceBill>> e : byDate.entrySet()) {
                LocalDate date = e.getKey();
                List<InvoiceBill> dayBills = e.getValue();
                BigDecimal dayTotalRaw = BigDecimal.ZERO;
                for (InvoiceBill b : dayBills) {
                    if (b.getNetAmount() != null) dayTotalRaw = dayTotalRaw.add(b.getNetAmount());
                }
                BigDecimal projected = currentRaw.add(dayTotalRaw);
                boolean wouldOverflow = cap != null && !currentBillIds.isEmpty()
                        && projected.setScale(0, RoundingMode.HALF_UP).compareTo(cap) > 0;
                log.info("[previewDayWise]   day={} dayTotal={} currentRaw={} projected={} cap={} willClose={}",
                        date, dayTotalRaw, currentRaw, projected, cap, wouldOverflow);
                // Close current group BEFORE adding this day if it would overflow.
                if (wouldOverflow) {
                    splits.add(DayWiseStatementPreview.SplitGroup.builder()
                            .index(splitIndex++)
                            .fromDate(currentFrom)
                            .toDate(currentTo)
                            .billCount(currentBillIds.size())
                            .billIds(new ArrayList<>(currentBillIds))
                            .total(currentRaw.setScale(0, RoundingMode.HALF_UP))
                            .exceedsCap(currentExceeds)
                            .build());
                    currentBillIds = new ArrayList<>();
                    currentRaw = BigDecimal.ZERO;
                    currentDayCount = 0;
                    currentFrom = null;
                    currentTo = null;
                    currentExceeds = false;
                }
                if (currentFrom == null) currentFrom = date;
                currentTo = date;
                currentDayCount++;
                for (InvoiceBill b : dayBills) currentBillIds.add(b.getId());
                currentRaw = currentRaw.add(dayTotalRaw);
                if (cap != null && currentDayCount == 1 && currentRaw.setScale(0, RoundingMode.HALF_UP).compareTo(cap) > 0) {
                    currentExceeds = true;
                }
            }
            if (!currentBillIds.isEmpty()) {
                splits.add(DayWiseStatementPreview.SplitGroup.builder()
                        .index(splitIndex)
                        .fromDate(currentFrom)
                        .toDate(currentTo)
                        .billCount(currentBillIds.size())
                        .billIds(currentBillIds)
                        .total(currentRaw.setScale(0, RoundingMode.HALF_UP))
                        .exceedsCap(currentExceeds)
                        .build());
            }
            log.info("[previewDayWise] result: customer={} splits={} totals={}",
                    customerId,
                    splits.size(),
                    splits.stream().map(g -> g.getTotal().toString()).toList());
        }

        return DayWiseStatementPreview.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .fromDate(fromDate)
                .toDate(toDate)
                .maxAmount(maxAmount)
                .totalBills(bills.size())
                .grandTotal(grandTotalRaw.setScale(0, RoundingMode.HALF_UP))
                .days(days)
                .suggestedSplits(splits)
                .build();
    }

    /**
     * Create N statements at once, each from an explicit list of bill IDs. Rolls back the
     * whole batch on any failure. Use case: day-wise cap splitting on a customer's period —
     * the UI assembles the per-statement billIds (auto-suggested, user-tweakable) and submits
     * them here.
     *
     * Validation: no group may be empty; no billId may appear in two groups (otherwise the
     * second group's findUnlinkedCreditBillsByIds would silently drop it because the first
     * group already linked the bill). PDFs are generated outside this @Transactional block
     * so a PDF/S3 hiccup never rolls back valid statement rows — failed PDFs can be
     * regenerated later via the existing /{id}/generate-pdf endpoint.
     */
    @Transactional
    public List<Statement> generateBatch(Long customerId, ReportLayout reportLayout,
                                         List<List<Long>> splitBillIdGroups) {
        if (splitBillIdGroups == null || splitBillIdGroups.isEmpty()) {
            throw new BusinessException("At least one statement group is required");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        // Detect overlapping billIds across groups upfront — a duplicate would silently
        // disappear from the second group (statement IS NULL filter).
        Set<Long> seen = new HashSet<>();
        for (List<Long> group : splitBillIdGroups) {
            if (group == null || group.isEmpty()) {
                throw new BusinessException("Each statement group must contain at least one bill");
            }
            for (Long id : group) {
                if (!seen.add(id)) {
                    throw new BusinessException("Bill " + id + " appears in more than one statement group");
                }
            }
        }

        Long scid = SecurityUtils.getScid();
        List<Statement> created = new ArrayList<>(splitBillIdGroups.size());
        for (List<Long> group : splitBillIdGroups) {
            List<InvoiceBill> bills = invoiceBillRepository.findUnlinkedCreditBillsByIds(group, scid);
            if (bills.size() != group.size()) {
                throw new BusinessException("One or more bills are no longer available — please re-preview");
            }
            for (InvoiceBill bill : bills) {
                if (!bill.getCustomer().getId().equals(customerId)) {
                    throw new BusinessException("Bill " + bill.getId() + " does not belong to customer " + customerId);
                }
            }
            LocalDate fromDate = bills.stream()
                    .map(b -> b.getDate().toLocalDate())
                    .min(LocalDate::compareTo).orElseThrow();
            LocalDate toDate = bills.stream()
                    .map(b -> b.getDate().toLocalDate())
                    .max(LocalDate::compareTo).orElseThrow();
            created.add(createStatementFromBills(customer, bills, fromDate, toDate, reportLayout));
        }
        return created;
    }

    /**
     * Recompute number_of_bills, total_amount, net_amount (rounded), rounding_amount,
     * total_quantity, and balance_amount = net - received from currently-linked bills.
     * Caller must persist the statement after. Does NOT touch status, statementNo, dates,
     * or received_amount — those are caller decisions. Used by regenerate flow and by
     * OrphanBillService.autoFix.
     */
    public void recomputeStatementTotals(Statement statement) {
        List<InvoiceBill> bills = invoiceBillRepository.findByStatementId(statement.getId());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill bill : bills) {
            if (bill.getNetAmount() != null) {
                totalAmount = totalAmount.add(bill.getNetAmount());
            }
        }
        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);

        statement.setNumberOfBills(bills.size());
        statement.setTotalAmount(totalAmount);
        statement.setNetAmount(netAmount);
        statement.setRoundingAmount(roundingAmount);
        BigDecimal received = statement.getReceivedAmount() != null
                ? statement.getReceivedAmount() : BigDecimal.ZERO;
        statement.setBalanceAmount(netAmount.subtract(received));

        if (!bills.isEmpty()) {
            List<Long> ids = bills.stream().map(InvoiceBill::getId).toList();
            statement.setTotalQuantity(invoiceBillRepository.sumQuantityByBillIds(ids));
        } else {
            statement.setTotalQuantity(BigDecimal.ZERO);
        }
    }

    /**
     * Approve a DRAFT statement: set status to NOT_PAID and auto-generate PDF.
     */
    @Transactional
    public Statement approveStatement(Long id) {
        Statement stmt = getStatementById(id);
        if (!"DRAFT".equals(stmt.getStatus())) {
            throw new BusinessException("Only DRAFT statements can be approved");
        }
        stmt.setStatus("NOT_PAID");
        Statement saved = statementRepository.save(stmt);
        generateAndStorePdf(saved.getId());
        return saved;
    }

    /**
     * Rename a statement's human-readable number (S-XXXXX). Allowed only on DRAFT and
     * NOT_PAID statements — PAID is frozen because the customer has already received
     * the document under the original number. Globally unique constraint on
     * statement_no is enforced both here (friendly 400) and at the DB.
     */
    @Transactional
    public Statement updateStatementNo(Long id, String newNo) {
        Statement stmt = getStatementById(id);
        if ("PAID".equals(stmt.getStatus())) {
            throw new BusinessException("Cannot rename a PAID statement");
        }
        if (newNo == null) {
            throw new BusinessException("Statement number is required");
        }
        String trimmed = newNo.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("Statement number cannot be blank");
        }
        if (trimmed.equals(stmt.getStatementNo())) {
            return stmt; // no-op
        }
        // Globally unique — check existing rows.
        statementRepository.findByStatementNo(trimmed).ifPresent(other -> {
            if (!Objects.equals(other.getId(), id)) {
                throw new BusinessException("Statement number already in use: " + trimmed);
            }
        });
        stmt.setStatementNo(trimmed);
        return statementRepository.save(stmt);
    }

    /**
     * Preview unlinked credit bills matching the given filters (without creating a statement).
     */
    @Transactional(readOnly = true)
    public List<InvoiceBill> previewBills(Long customerId, LocalDate fromDate, LocalDate toDate,
                                          Long vehicleId, Long productId) {
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        if (vehicleId != null && productId != null) {
            return invoiceBillRepository.findUnlinkedCreditBillsByVehicleAndProduct(
                    customerId, fromDateTime, toDateTime, vehicleId, productId);
        } else if (vehicleId != null) {
            return invoiceBillRepository.findUnlinkedCreditBillsByVehicle(
                    customerId, fromDateTime, toDateTime, vehicleId);
        } else if (productId != null) {
            return invoiceBillRepository.findUnlinkedCreditBillsByProduct(
                    customerId, fromDateTime, toDateTime, productId);
        } else {
            return invoiceBillRepository.findUnlinkedCreditBills(
                    customerId, fromDateTime, toDateTime);
        }
    }

    /**
     * Remove a disputed/unfit bill from its statement and recalculate totals.
     */
    @Transactional
    public Statement removeBillFromStatement(Long statementId, Long invoiceBillId) {
        Statement statement = statementRepository.findByIdForUpdate(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found"));

        InvoiceBill bill = invoiceBillRepository.findByIdForUpdate(invoiceBillId)
                .orElseThrow(() -> new RuntimeException("Invoice bill not found"));

        if (bill.getStatement() == null || !bill.getStatement().getId().equals(statementId)) {
            throw new BusinessException("Bill does not belong to this statement");
        }

        // Unlink the bill
        bill.setStatement(null);
        invoiceBillRepository.save(bill);

        // Recalculate statement totals from remaining bills
        List<InvoiceBill> remainingBills = invoiceBillRepository.findByStatementId(statementId);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill remaining : remainingBills) {
            if (remaining.getNetAmount() != null) {
                totalAmount = totalAmount.add(remaining.getNetAmount());
            }
        }

        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);
        BigDecimal balanceAmount = netAmount.subtract(statement.getReceivedAmount());

        statement.setNumberOfBills(remainingBills.size());
        statement.setTotalAmount(totalAmount);
        statement.setRoundingAmount(roundingAmount);
        statement.setNetAmount(netAmount);
        statement.setBalanceAmount(balanceAmount);

        // Recalculate total quantity from remaining bills
        if (!remainingBills.isEmpty()) {
            List<Long> remainingBillIds = remainingBills.stream().map(InvoiceBill::getId).toList();
            statement.setTotalQuantity(invoiceBillRepository.sumQuantityByBillIds(remainingBillIds));
        } else {
            statement.setTotalQuantity(BigDecimal.ZERO);
        }

        // Check if already fully paid after removal
        if (balanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            statement.setStatus("PAID");
            statement.setBalanceAmount(BigDecimal.ZERO);
            // Mark remaining bills as PAID
            for (InvoiceBill remaining : remainingBills) {
                remaining.setPaymentStatus(com.stopforfuel.backend.enums.PaymentStatus.PAID);
                invoiceBillRepository.save(remaining);
            }
        }

        return statementRepository.save(statement);
    }

    /**
     * Get all bills linked to a statement, useful for statement detail/print view.
     */
    @Transactional(readOnly = true)
    public List<InvoiceBill> getStatementBills(Long statementId) {
        return invoiceBillRepository.findByStatementId(statementId);
    }

    /**
     * Consolidated customer-wise PDF for VEHICLE_WISE customers. Pulls ALL credit bills for the
     * customer in the period (regardless of statementGrouping or whether bills are already linked
     * to per-vehicle statements) and renders one combined preview PDF. Pure read-only — never
     * touches the DB. Used by the customer detail page's "Download consolidated PDF" button so
     * the owner can hand a single document to the customer that reflects total April spending,
     * even though the persisted statements are vehicle-wise.
     */
    @Transactional(readOnly = true)
    public byte[] consolidatedCustomerPdf(Long customerId, LocalDate fromDate, LocalDate toDate) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        List<InvoiceBill> bills = invoiceBillRepository.findCreditBillsByCustomerAndDateRange(
                customerId, fromDateTime, toDateTime,
                customer.getScid() != null ? customer.getScid() : SecurityUtils.getScid());

        if (bills.isEmpty()) {
            throw new BusinessException("No credit bills found for " + customer.getName()
                    + " between " + fromDate + " and " + toDate);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill bill : bills) {
            if (bill.getNetAmount() != null) totalAmount = totalAmount.add(bill.getNetAmount());
        }
        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);

        Statement preview = new Statement();
        preview.setScid(customer.getScid());
        preview.setStatementNo("CONSOLIDATED");
        preview.setCustomer(customer);
        preview.setFromDate(fromDate);
        preview.setToDate(toDate);
        preview.setStatementDate(LocalDate.now());
        preview.setNumberOfBills(bills.size());
        preview.setTotalAmount(totalAmount);
        preview.setRoundingAmount(roundingAmount);
        preview.setNetAmount(netAmount);
        preview.setReceivedAmount(BigDecimal.ZERO);
        preview.setBalanceAmount(netAmount);
        preview.setStatus("NOT_PAID");
        List<Long> billIds = bills.stream().map(InvoiceBill::getId).toList();
        preview.setTotalQuantity(invoiceBillRepository.sumQuantityByBillIds(billIds));

        List<Company> companies = companyRepository.findByScid(
                customer.getScid() != null ? customer.getScid() : SecurityUtils.getScid());
        Company company = !companies.isEmpty() ? companies.get(0) : null;

        return pdfGenerator.generate(preview, bills, company, java.util.Collections.emptyList());
    }

    /**
     * Build the same statement PDF that {@link #generateStatement} + {@link #generateAndStorePdf}
     * would produce, but never persists a Statement row, never links bills, never uploads to S3,
     * and never consumes a statement-sequence number. Pure read-only — used for the
     * "Download PDF (Preview)" button in the Generate Statement dialog so cashiers can verify
     * the layout before committing.
     */
    @Transactional(readOnly = true)
    public byte[] previewStatementPdf(Long customerId, LocalDate fromDate, LocalDate toDate,
                                      Long vehicleId, Long productId, List<Long> billIds) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        List<InvoiceBill> bills;
        if (billIds != null && !billIds.isEmpty()) {
            bills = invoiceBillRepository.findUnlinkedCreditBillsByIds(billIds, SecurityUtils.getScid());
            for (InvoiceBill bill : bills) {
                if (!bill.getCustomer().getId().equals(customerId)) {
                    throw new BusinessException("Bill " + bill.getId() + " does not belong to customer " + customerId);
                }
            }
        } else {
            LocalDateTime fromDateTime = fromDate.atStartOfDay();
            LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);
            if (vehicleId != null && productId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByVehicleAndProduct(
                        customerId, fromDateTime, toDateTime, vehicleId, productId);
            } else if (vehicleId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByVehicle(
                        customerId, fromDateTime, toDateTime, vehicleId);
            } else if (productId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByProduct(
                        customerId, fromDateTime, toDateTime, productId);
            } else {
                bills = invoiceBillRepository.findUnlinkedCreditBills(
                        customerId, fromDateTime, toDateTime);
            }
        }

        if (bills.isEmpty()) {
            throw new BusinessException("No unlinked credit bills found for the selected filters");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill bill : bills) {
            if (bill.getNetAmount() != null) totalAmount = totalAmount.add(bill.getNetAmount());
        }
        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);

        Statement preview = new Statement();
        preview.setScid(customer.getScid());
        preview.setStatementNo("PREVIEW");
        preview.setCustomer(customer);
        preview.setFromDate(fromDate);
        preview.setToDate(toDate);
        preview.setStatementDate(LocalDate.now());
        preview.setNumberOfBills(bills.size());
        preview.setTotalAmount(totalAmount);
        preview.setRoundingAmount(roundingAmount);
        preview.setNetAmount(netAmount);
        preview.setReceivedAmount(BigDecimal.ZERO);
        preview.setBalanceAmount(netAmount);
        preview.setStatus("NOT_PAID");
        List<Long> invoiceBillIds = bills.stream().map(InvoiceBill::getId).toList();
        preview.setTotalQuantity(invoiceBillRepository.sumQuantityByBillIds(invoiceBillIds));

        List<Company> companies = companyRepository.findByScid(
                customer.getScid() != null ? customer.getScid() : SecurityUtils.getScid());
        Company company = !companies.isEmpty() ? companies.get(0) : null;

        return pdfGenerator.generate(preview, bills, company, java.util.Collections.emptyList());
    }

    @Transactional
    public Statement generateAndStorePdf(Long id) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + id));

        List<InvoiceBill> bills = invoiceBillRepository.findByStatementId(id);

        List<Company> companies = companyRepository.findByScid(
                statement.getScid() != null ? statement.getScid() : SecurityUtils.getScid());
        Company company = !companies.isEmpty() ? companies.get(0) : null;

        List<Payment> payments = paymentRepository.findByStatementId(id);

        byte[] pdfBytes = pdfGenerator.generate(statement, bills, company, payments);

        LocalDate date = statement.getStatementDate() != null ? statement.getStatementDate() : LocalDate.now();
        Long scid = statement.getScid() != null ? statement.getScid() : SecurityUtils.getScid();
        Long customerId = statement.getCustomer() != null ? statement.getCustomer().getId() : 0L;
        String safeStatementNo = statement.getStatementNo() != null
                ? statement.getStatementNo().replace("/", "-") : String.valueOf(id);
        String key = String.format("statements/%d/%d/%d/%02d/%s.pdf",
                scid, customerId, date.getYear(), date.getMonthValue(), safeStatementNo);

        // Delete old PDF if exists
        if (statement.getStatementPdfUrl() != null && !statement.getStatementPdfUrl().isEmpty()) {
            try { s3StorageService.delete(statement.getStatementPdfUrl()); } catch (Exception ignored) {}
        }

        s3StorageService.upload(key, pdfBytes, "application/pdf");
        statement.setStatementPdfUrl(key);
        return statementRepository.save(statement);
    }

    @Transactional(readOnly = true)
    public String getStatementPdfUrl(Long id) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + id));
        if (statement.getStatementPdfUrl() == null || statement.getStatementPdfUrl().isEmpty()) {
            throw new ResourceNotFoundException("No PDF generated for this statement");
        }
        return s3StorageService.getPresignedUrl(statement.getStatementPdfUrl());
    }

    @Transactional
    public void deleteStatement(Long id) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Statement not found"));

        // Unlink all bills first
        List<InvoiceBill> bills = invoiceBillRepository.findByStatementId(id);
        for (InvoiceBill bill : bills) {
            bill.setStatement(null);
            invoiceBillRepository.save(bill);
        }

        statementRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public StatementStats getStats() {
        LocalDate startOfThisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate startOfLastMonth = startOfThisMonth.minusMonths(1);

        Long scid = SecurityUtils.getScid();

        // Last month metrics
        long statementsLastMonth = statementRepository.countByStatementDateRange(startOfLastMonth, startOfThisMonth, scid);
        long paidLastMonth = statementRepository.countPaidByStatementDateRange(startOfLastMonth, startOfThisMonth, scid);
        BigDecimal amountGeneratedLastMonth = statementRepository.sumNetAmountByDateRange(startOfLastMonth, startOfThisMonth, scid);
        BigDecimal amountCollectedLastMonth = statementRepository.sumReceivedAmountByDateRange(startOfLastMonth, startOfThisMonth, scid);

        // All-time metrics
        long totalStatements = statementRepository.countByScid(scid);
        long totalPaid = statementRepository.countPaid(scid);
        double paidPercentage = totalStatements > 0 ? (totalPaid * 100.0) / totalStatements : 0;
        BigDecimal totalUnpaidAmount = statementRepository.sumUnpaidBalance(scid);
        BigDecimal totalNetAmount = statementRepository.sumNetAmount(scid);
        BigDecimal totalReceivedAmount = statementRepository.sumReceivedAmount(scid);
        double collectionRate = totalNetAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalReceivedAmount.multiply(BigDecimal.valueOf(100)).divide(totalNetAmount, 2, RoundingMode.HALF_UP).doubleValue()
                : 0;
        BigDecimal avgStatementAmount = statementRepository.avgNetAmount(scid);

        return new StatementStats(
                statementsLastMonth, paidLastMonth, amountGeneratedLastMonth, amountCollectedLastMonth,
                totalStatements, totalPaid, paidPercentage, totalUnpaidAmount,
                totalNetAmount, totalReceivedAmount, collectionRate, avgStatementAmount
        );
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getRecommendedLimits(Long customerId, int count) {
        List<Statement> recent = statementRepository.findRecentByCustomerId(customerId, PageRequest.of(0, count));

        if (recent.isEmpty()) {
            return Map.of("recommendedCreditLimit", BigDecimal.ZERO,
                          "recommendedMonthlyConsumption", BigDecimal.ZERO,
                          "statementCount", BigDecimal.ZERO);
        }

        BigDecimal avgNetAmount = recent.stream()
                .map(Statement::getNetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recent.size()), 0, RoundingMode.HALF_UP);

        BigDecimal avgQuantity = recent.stream()
                .map(Statement::getTotalQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);

        return Map.of("recommendedCreditLimit", avgNetAmount,
                      "recommendedMonthlyConsumption", avgQuantity,
                      "statementCount", BigDecimal.valueOf(recent.size()));
    }
}
