package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.BillType;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatementAutoGenerationService {

    private final CustomerRepository customerRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;
    private final VehicleRepository vehicleRepository;
    private final BillSequenceService billSequenceService;

    /**
     * Called from ShiftService.approveAndClose() after a shift is closed.
     * Detects if the shift spans a statement boundary and auto-generates DRAFT statements.
     */
    @Transactional
    public int onShiftClosed(Shift shift) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return 0;
        }

        int totalDrafts = 0;
        LocalDate startDate = shift.getStartTime().toLocalDate();
        LocalDate endDate = shift.getEndTime().toLocalDate();

        // Check month boundary: shift starts in one month, ends in another
        boolean crossesMonthBoundary = startDate.getMonth() != endDate.getMonth()
                || startDate.getYear() != endDate.getYear();

        // Check biweekly boundary: shift starts on/before 15th, ends on/after 16th (same month)
        boolean crossesBiweeklyBoundary = !crossesMonthBoundary
                && startDate.getDayOfMonth() <= 15
                && endDate.getDayOfMonth() >= 16;

        if (crossesMonthBoundary) {
            // Generate for MONTHLY customers: full previous month
            YearMonth ym = YearMonth.from(startDate);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            totalDrafts += generateDraftsForPeriod(from, to, "MONTHLY", shift.getScid());

            // Also generate for BIWEEKLY customers: second half of the month (16th to end)
            if (startDate.getDayOfMonth() >= 16) {
                LocalDate biweeklyFrom = ym.atDay(16);
                totalDrafts += generateDraftsForPeriod(biweeklyFrom, to, "BIWEEKLY", shift.getScid());
            }
        }

        if (crossesBiweeklyBoundary) {
            // Generate for BIWEEKLY customers: first half (1st to 15th)
            YearMonth ym = YearMonth.from(startDate);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atDay(15);
            totalDrafts += generateDraftsForPeriod(from, to, "BIWEEKLY", shift.getScid());
        }

        if (totalDrafts > 0) {
            log.info("Auto-generated {} DRAFT statement(s) after shift {} closed", totalDrafts, shift.getId());
        }

        return totalDrafts;
    }

    /**
     * Manual trigger for admin to generate drafts on-demand.
     * Determines date range based on current date for each frequency.
     */
    @Transactional
    public int generateDraftsManually(Long scid) {
        int totalDrafts = 0;
        LocalDate today = LocalDate.now();

        // Monthly: previous month
        YearMonth prevMonth = YearMonth.from(today).minusMonths(1);
        totalDrafts += generateDraftsForPeriod(prevMonth.atDay(1), prevMonth.atEndOfMonth(), "MONTHLY", scid);

        // Biweekly: determine which half we're in
        if (today.getDayOfMonth() <= 15) {
            // We're in first half — generate for second half of previous month
            YearMonth pm = YearMonth.from(today).minusMonths(1);
            totalDrafts += generateDraftsForPeriod(pm.atDay(16), pm.atEndOfMonth(), "BIWEEKLY", scid);
        } else {
            // We're in second half — generate for first half of current month
            YearMonth cm = YearMonth.from(today);
            totalDrafts += generateDraftsForPeriod(cm.atDay(1), cm.atDay(15), "BIWEEKLY", scid);
        }

        log.info("Manual draft generation completed: {} drafts created", totalDrafts);
        return totalDrafts;
    }

    /**
     * Manual trigger with caller-supplied date range and frequency selector.
     * - Both dates null/blank AND frequency null/blank → fall through to the date-deriving overload.
     * - Otherwise both dates are required; frequency in {MONTHLY, BIWEEKLY, BOTH}; null/blank treated as BOTH.
     * - Bills are picked via shift-open-time windowing (see InvoiceBillRepository.findUnlinkedCreditBills),
     *   so callers should usually wait for the last shift in the range to close before running.
     */
    @Transactional
    public int generateDraftsManually(Long scid, LocalDate fromDate, LocalDate toDate, String frequency) {
        boolean datesEmpty = fromDate == null && toDate == null;
        boolean freqEmpty = frequency == null || frequency.isBlank();
        if (datesEmpty && freqEmpty) {
            return generateDraftsManually(scid);
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate must both be supplied (or both omitted)");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        String normalized = freqEmpty ? "BOTH" : frequency.trim().toUpperCase();
        int totalDrafts = 0;
        if ("MONTHLY".equals(normalized) || "BOTH".equals(normalized)) {
            totalDrafts += generateDraftsForPeriod(fromDate, toDate, "MONTHLY", scid);
        }
        if ("BIWEEKLY".equals(normalized) || "BOTH".equals(normalized)) {
            totalDrafts += generateDraftsForPeriod(fromDate, toDate, "BIWEEKLY", scid);
        }
        if (!"MONTHLY".equals(normalized) && !"BIWEEKLY".equals(normalized) && !"BOTH".equals(normalized)) {
            throw new IllegalArgumentException("frequency must be MONTHLY, BIWEEKLY, or BOTH; got " + frequency);
        }
        log.info("Manual draft generation (custom) {}..{} freq={} → {} drafts created",
                fromDate, toDate, normalized, totalDrafts);
        return totalDrafts;
    }

    /**
     * Generate DRAFT statements for all eligible customers matching the given frequency.
     * Order: customer.statementOrder ascending (nulls last), then id ascending for stability.
     * Skip sentinel: any negative statementOrder (e.g. -1) excludes the customer from auto-gen.
     */
    private int generateDraftsForPeriod(LocalDate fromDate, LocalDate toDate, String frequency, Long scid) {
        // Get all active customers with this frequency, excluding BILL_WISE grouping
        List<Customer> customers = customerRepository.findByStatusAndScid(EntityStatus.ACTIVE, scid);

        List<Customer> eligible = customers.stream()
                .filter(c -> frequency.equals(c.getStatementFrequency()))
                .filter(c -> !"BILL_WISE".equals(c.getStatementGrouping()))
                .filter(c -> c.getStatementOrder() == null || c.getStatementOrder() >= 0)
                .sorted(Comparator
                        .comparing(Customer::getStatementOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Customer::getId))
                .toList();

        int draftsCreated = 0;

        for (Customer customer : eligible) {
            try {
                String grouping = customer.getStatementGrouping();

                if ("VEHICLE_WISE".equals(grouping)) {
                    draftsCreated += generateVehicleWiseDrafts(customer, fromDate, toDate);
                } else {
                    // CUSTOMER_WISE (default)
                    draftsCreated += generateCustomerWiseDraft(customer, fromDate, toDate);
                }
            } catch (Exception e) {
                log.error("Failed to generate draft for customer {} ({}): {}",
                        customer.getId(), customer.getName(), e.getMessage());
            }
        }

        return draftsCreated;
    }

    private int generateCustomerWiseDraft(Customer customer, LocalDate fromDate, LocalDate toDate) {
        // Duplicate prevention
        if (statementRepository.existsByCustomerIdAndFromDateAndToDateAndScid(
                customer.getId(), fromDate, toDate, customer.getScid())) {
            return 0;
        }

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        List<InvoiceBill> bills = invoiceBillRepository.findUnlinkedCreditBills(
                customer.getId(), fromDateTime, toDateTime);

        if (bills.isEmpty()) {
            return 0;
        }

        createDraftStatement(customer, fromDate, toDate, bills);
        return 1;
    }

    private int generateVehicleWiseDrafts(Customer customer, LocalDate fromDate, LocalDate toDate) {
        List<Vehicle> vehicles = vehicleRepository.findByCustomerId(customer.getId());
        int draftsCreated = 0;

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        for (Vehicle vehicle : vehicles) {
            // Duplicate prevention per vehicle
            if (statementRepository.existsByCustomerIdAndFromDateAndToDateAndScid(
                    customer.getId(), fromDate, toDate, customer.getScid())) {
                // For vehicle-wise, we need a more specific check — but since each vehicle
                // gets its own statement with the same fromDate/toDate, we check if bills
                // for this vehicle are already linked
            }

            List<InvoiceBill> bills = invoiceBillRepository.findUnlinkedCreditBillsByVehicle(
                    customer.getId(), fromDateTime, toDateTime, vehicle.getId());

            if (!bills.isEmpty()) {
                createDraftStatement(customer, fromDate, toDate, bills);
                draftsCreated++;
            }
        }

        return draftsCreated;
    }

    private void createDraftStatement(Customer customer, LocalDate fromDate, LocalDate toDate,
                                       List<InvoiceBill> bills) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill bill : bills) {
            if (bill.getNetAmount() != null) {
                totalAmount = totalAmount.add(bill.getNetAmount());
            }
        }

        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);

        String nextStatementNo = billSequenceService.getNextBillNo(BillType.STMT);

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
        statement.setStatus("DRAFT");

        // Compute total quantity from invoice products
        List<Long> billIds = bills.stream().map(InvoiceBill::getId).toList();
        statement.setTotalQuantity(invoiceBillRepository.sumQuantityByBillIds(billIds));

        Statement saved = statementRepository.save(statement);

        for (InvoiceBill bill : bills) {
            bill.setStatement(saved);
            invoiceBillRepository.save(bill);
        }
    }
}
