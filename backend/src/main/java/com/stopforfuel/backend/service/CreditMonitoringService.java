package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CreditPolicy;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.CustomerBlockEvent;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditMonitoringService {

    private final CustomerRepository customerRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final StatementRepository statementRepository;
    private final CustomerBlockEventRepository blockEventRepository;
    private final CreditPolicyService creditPolicyService;

    // ─── Credit Health Computation ────────────────────────────────

    /**
     * Computes credit health for a single customer.
     * Risk levels:
     *   HIGH   = utilization >= blockPercent OR aging >= blockDays OR BLOCKED
     *   MEDIUM = utilization >= warnPercent OR aging >= watchDays
     *   LOW    = everything else
     */
    @Transactional(readOnly = true)
    public CreditHealth computeCreditHealth(Long customerId) {
        Customer customer = customerRepository.findByIdAndScid(customerId, SecurityUtils.getScid())
                .orElseThrow(() -> new com.stopforfuel.backend.exception.ResourceNotFoundException(
                        "Customer not found: " + customerId));
        return computeCreditHealthInternal(customer);
    }

    private CreditHealth computeCreditHealthInternal(Customer customer) {
        CreditPolicy policy = creditPolicyService.getEffectivePolicy(customer);

        BigDecimal totalBilled = invoiceBillRepository.sumAllCreditBillsByCustomer(customer.getId());
        BigDecimal totalPaid = paymentRepository.sumAllPaymentsByCustomer(customer.getId());
        BigDecimal ledgerBalance = totalBilled.subtract(totalPaid);

        BigDecimal creditLimit = customer.getCreditLimitAmount() != null
                ? customer.getCreditLimitAmount() : BigDecimal.ZERO;

        // Utilization %
        BigDecimal utilizationPercent = BigDecimal.ZERO;
        if (creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            utilizationPercent = ledgerBalance
                    .multiply(new BigDecimal(100))
                    .divide(creditLimit, 2, RoundingMode.HALF_UP);
        }

        // Oldest unpaid days
        Optional<LocalDateTime> oldestUnpaidDate = invoiceBillRepository.findOldestUnpaidBillDate(customer.getId());
        long oldestUnpaidDays = oldestUnpaidDate
                .map(d -> ChronoUnit.DAYS.between(d.toLocalDate(), java.time.LocalDate.now()))
                .orElse(0L);

        // Risk level
        String riskLevel;
        String suggestedAction;

        boolean isBlocked = customer.getStatus() == EntityStatus.BLOCKED;
        boolean utilizationCritical = creditLimit.compareTo(BigDecimal.ZERO) > 0
                && utilizationPercent.compareTo(new BigDecimal(policy.getUtilizationBlockPercent())) >= 0;
        boolean agingCritical = oldestUnpaidDays >= policy.getAgingBlockDays();
        boolean utilizationWarning = creditLimit.compareTo(BigDecimal.ZERO) > 0
                && utilizationPercent.compareTo(new BigDecimal(policy.getUtilizationWarnPercent())) >= 0;
        boolean agingWarning = oldestUnpaidDays >= policy.getAgingWatchDays();

        if (isBlocked || utilizationCritical || agingCritical) {
            riskLevel = "HIGH";
            if (isBlocked) {
                suggestedAction = "Customer is blocked. Review and collect payment before unblocking.";
            } else if (agingCritical) {
                suggestedAction = "Unpaid bills aging " + oldestUnpaidDays + " days. Consider blocking.";
            } else {
                suggestedAction = "Credit utilization at " + utilizationPercent + "%. Consider blocking.";
            }
        } else if (utilizationWarning || agingWarning) {
            riskLevel = "MEDIUM";
            if (agingWarning) {
                suggestedAction = "Unpaid bills aging " + oldestUnpaidDays + " days. Send payment reminder.";
            } else {
                suggestedAction = "Credit utilization at " + utilizationPercent + "%. Monitor closely.";
            }
        } else {
            riskLevel = "LOW";
            suggestedAction = "OK";
        }

        CreditHealth health = new CreditHealth();
        health.setCustomerId(customer.getId());
        health.setCustomerName(customer.getName());
        health.setStatus(customer.getStatus() != null ? customer.getStatus().name() : "ACTIVE");
        health.setRiskLevel(riskLevel);
        health.setCreditLimit(creditLimit);
        health.setLedgerBalance(ledgerBalance);
        health.setTotalBilled(totalBilled);
        health.setTotalPaid(totalPaid);
        health.setUtilizationPercent(utilizationPercent);
        health.setOldestUnpaidDays(oldestUnpaidDays);
        health.setSuggestedAction(suggestedAction);
        health.setBlockCount(customer.getBlockCount() != null ? customer.getBlockCount() : 0);
        health.setLastBlockedAt(customer.getLastBlockedAt());
        health.setPolicyName(policy.getPolicyName());
        health.setCategoryType(customer.getCustomerCategory() != null
                ? customer.getCustomerCategory().getCategoryType() : null);
        health.setCategoryName(customer.getCustomerCategory() != null
                ? customer.getCustomerCategory().getCategoryName() : null);
        health.setGroupName(customer.getGroup() != null ? customer.getGroup().getGroupName() : null);
        health.setStatementFrequency(customer.getStatementFrequency());
        return health;
    }

    // ─── Watchlist ────────────────────────────────────────────────

    /**
     * Returns customers at MEDIUM or HIGH risk, sorted by risk (HIGH first) then by outstanding.
     */
    @Transactional(readOnly = true)
    public List<CreditHealth> getWatchlist() {
        List<Customer> allCustomers = customerRepository.findAllByScid(SecurityUtils.getScid());
        List<CreditHealth> watchlist = new ArrayList<>();

        for (Customer customer : allCustomers) {
            // Skip customers with no credit limit (cash-only customers)
            if ((customer.getCreditLimitAmount() == null || customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) == 0)
                    && customer.getStatus() != EntityStatus.BLOCKED) {
                continue;
            }

            CreditHealth health = computeCreditHealthInternal(customer);
            if ("HIGH".equals(health.getRiskLevel()) || "MEDIUM".equals(health.getRiskLevel())) {
                watchlist.add(health);
            }
        }

        // Sort: HIGH before MEDIUM, then by ledgerBalance desc
        watchlist.sort((a, b) -> {
            int riskCmp = riskOrder(a.getRiskLevel()) - riskOrder(b.getRiskLevel());
            if (riskCmp != 0) return riskCmp;
            return b.getLedgerBalance().compareTo(a.getLedgerBalance());
        });

        return watchlist;
    }

    private int riskOrder(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    // ─── Reconciliation Summary ───────────────────────────────────

    /**
     * Full credit loop for one customer: limit -> consumed -> billed -> paid -> balance -> aging -> action.
     */
    @Transactional(readOnly = true)
    public ReconciliationSummary getReconciliationSummary(Long customerId) {
        Customer customer = customerRepository.findByIdAndScid(customerId, SecurityUtils.getScid())
                .orElseThrow(() -> new com.stopforfuel.backend.exception.ResourceNotFoundException(
                        "Customer not found: " + customerId));

        CreditHealth health = computeCreditHealthInternal(customer);

        ReconciliationSummary summary = new ReconciliationSummary();
        summary.setHealth(health);
        summary.setCreditLimitLiters(customer.getCreditLimitLiters());
        summary.setConsumedLiters(customer.getConsumedLiters());
        summary.setStatementFrequency(customer.getStatementFrequency());
        summary.setStatementGrouping(customer.getStatementGrouping());
        summary.setCategoryType(customer.getCustomerCategory() != null
                ? customer.getCustomerCategory().getCategoryType() : null);
        summary.setCategoryName(customer.getCustomerCategory() != null
                ? customer.getCustomerCategory().getCategoryName() : null);
        summary.setGroupName(customer.getGroup() != null ? customer.getGroup().getGroupName() : null);
        summary.setBlockHistory(getBlockHistory(customerId));

        return summary;
    }

    // ─── Block History ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlockEventDTO> getBlockHistory(Long customerId) {
        return blockEventRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toBlockEventDTO)
                .toList();
    }

    private BlockEventDTO toBlockEventDTO(CustomerBlockEvent event) {
        BlockEventDTO dto = new BlockEventDTO();
        dto.setId(event.getId());
        dto.setEventType(event.getEventType());
        dto.setTriggerType(event.getTriggerType());
        dto.setReason(event.getReason());
        dto.setNotes(event.getNotes());
        dto.setPerformedByName(event.getPerformedBy() != null ? event.getPerformedBy().getUsername() : "System");
        dto.setPreviousStatus(event.getPreviousStatus());
        dto.setCreatedAt(event.getCreatedAt());
        return dto;
    }

    // ─── Scheduled Auto-Block Scan ────────────────────────────────

    /**
     * Daily scan at 6 AM: checks all ACTIVE credit customers across all tenants.
     * Blocks those who breach their effective policy thresholds.
     */
    @Scheduled(cron = "0 0 6 * * ?")
    @Transactional
    public void runDailyAutoBlockScan() {
        log.info("Starting daily credit auto-block scan...");
        List<Long> scids = customerRepository.findDistinctScids();
        int totalBlocked = 0;

        for (Long scid : scids) {
            List<Customer> activeCustomers = customerRepository.findByStatusAndScid(EntityStatus.ACTIVE, scid);

            for (Customer customer : activeCustomers) {
                // Skip customers without credit limits
                if (customer.getCreditLimitAmount() == null
                        || customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                CreditPolicy policy = creditPolicyService.getEffectivePolicy(customer);
                if (!Boolean.TRUE.equals(policy.getAutoBlockEnabled())) {
                    continue;
                }

                String blockReason = evaluateBlockTriggers(customer, policy);
                if (blockReason != null) {
                    blockCustomerWithEvent(customer, "AUTO_SCHEDULED", blockReason, null);
                    totalBlocked++;
                }
            }
        }

        log.info("Daily auto-block scan complete. Blocked {} customers.", totalBlocked);
    }

    /**
     * Manual trigger for the auto-block scan (admin use).
     * Runs for the current tenant only.
     */
    @Transactional
    public int runManualScan() {
        Long scid = SecurityUtils.getScid();
        List<Customer> activeCustomers = customerRepository.findByStatusAndScid(EntityStatus.ACTIVE, scid);
        int blocked = 0;

        for (Customer customer : activeCustomers) {
            if (customer.getCreditLimitAmount() == null
                    || customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            CreditPolicy policy = creditPolicyService.getEffectivePolicy(customer);
            if (!Boolean.TRUE.equals(policy.getAutoBlockEnabled())) {
                continue;
            }

            String blockReason = evaluateBlockTriggers(customer, policy);
            if (blockReason != null) {
                blockCustomerWithEvent(customer, "AUTO_SCHEDULED", blockReason, null);
                blocked++;
            }
        }

        return blocked;
    }

    /**
     * Evaluates whether a customer should be auto-blocked based on their effective policy.
     * Returns a reason string if blocked, null if OK.
     */
    private String evaluateBlockTriggers(Customer customer, CreditPolicy policy) {
        Long custId = customer.getId();

        // 1. Amount-based: ledger balance > credit limit (utilization >= blockPercent)
        BigDecimal totalBilled = invoiceBillRepository.sumAllCreditBillsByCustomer(custId);
        BigDecimal totalPaid = paymentRepository.sumAllPaymentsByCustomer(custId);
        BigDecimal ledgerBalance = totalBilled.subtract(totalPaid);
        BigDecimal creditLimit = customer.getCreditLimitAmount();

        if (creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal utilizationPercent = ledgerBalance
                    .multiply(new BigDecimal(100))
                    .divide(creditLimit, 2, RoundingMode.HALF_UP);
            if (utilizationPercent.compareTo(new BigDecimal(policy.getUtilizationBlockPercent())) >= 0) {
                return "Credit utilization " + utilizationPercent + "% exceeds "
                        + policy.getUtilizationBlockPercent() + "% threshold. "
                        + "Balance: Rs." + ledgerBalance.toPlainString()
                        + ", Limit: Rs." + creditLimit.toPlainString();
            }
        }

        // 2. Liters exceeded
        if (customer.getCreditLimitLiters() != null
                && customer.getConsumedLiters() != null
                && customer.getCreditLimitLiters().compareTo(BigDecimal.ZERO) > 0
                && customer.getConsumedLiters().compareTo(customer.getCreditLimitLiters()) >= 0) {
            return "Consumed liters " + customer.getConsumedLiters().toPlainString()
                    + " exceeds limit " + customer.getCreditLimitLiters().toPlainString();
        }

        // 3. Aging: oldest unpaid bill > agingBlockDays
        Optional<LocalDateTime> oldestUnpaidDate = invoiceBillRepository.findOldestUnpaidBillDate(custId);
        if (oldestUnpaidDate.isPresent()) {
            long daysOld = ChronoUnit.DAYS.between(oldestUnpaidDate.get().toLocalDate(), java.time.LocalDate.now());
            if (daysOld >= policy.getAgingBlockDays()) {
                return "Unpaid bill aging " + daysOld + " days exceeds "
                        + policy.getAgingBlockDays() + "-day threshold";
            }
        }

        return null;
    }

    // ─── Block/Unblock with Event Recording ───────────────────────

    /**
     * Blocks a customer and records a CustomerBlockEvent.
     * Used by both scheduled scan and modified CustomerService methods.
     */
    @Transactional
    public void blockCustomerWithEvent(Customer customer, String triggerType, String reason, String notes) {
        String previousStatus = customer.getStatus() != null ? customer.getStatus().name() : "ACTIVE";
        customer.setStatus(EntityStatus.BLOCKED);
        customer.setLastBlockedAt(LocalDateTime.now());
        customer.setBlockCount(customer.getBlockCount() != null ? customer.getBlockCount() + 1 : 1);
        customerRepository.save(customer);

        CustomerBlockEvent event = new CustomerBlockEvent();
        event.setCustomer(customer);
        event.setScid(customer.getScid());
        event.setEventType("BLOCKED");
        event.setTriggerType(triggerType);
        event.setReason(reason);
        event.setNotes(notes);
        event.setPreviousStatus(previousStatus);
        // performedBy is null for system actions
        blockEventRepository.save(event);
    }

    @Transactional
    public void recordUnblockEvent(Customer customer, String notes) {
        CustomerBlockEvent event = new CustomerBlockEvent();
        event.setCustomer(customer);
        event.setScid(customer.getScid());
        event.setEventType("UNBLOCKED");
        event.setTriggerType("MANUAL");
        event.setReason("Manual unblock by admin");
        event.setNotes(notes);
        event.setPreviousStatus("BLOCKED");
        blockEventRepository.save(event);
    }

    // ─── Credit Monitoring Dashboard ──────────────────────────────

    @Transactional(readOnly = true)
    public CreditMonitoringDashboard getDashboard() {
        Long scid = SecurityUtils.getScid();
        List<Customer> allCustomers = customerRepository.findAllByScid(scid);

        List<CreditCustomerRow> localRows = new ArrayList<>();
        List<CreditCustomerRow> statementRows = new ArrayList<>();
        int totalLocalOverdue = 0, totalStatementOverdue = 0;
        BigDecimal totalLocalAmount = BigDecimal.ZERO, totalStatementAmount = BigDecimal.ZERO;

        for (Customer c : allCustomers) {
            // Skip customers with no credit limit
            if (c.getCreditLimitAmount() == null || c.getCreditLimitAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean isStatement = c.getStatementFrequency() != null && !c.getStatementFrequency().isBlank();

            CreditCustomerRow row = new CreditCustomerRow();
            row.setCustomerId(c.getId());
            row.setCustomerName(c.getName());
            row.setStatus(c.getStatus() != null ? c.getStatus().name() : "ACTIVE");
            row.setRepaymentDays(c.getRepaymentDays());
            row.setCreditLimit(c.getCreditLimitAmount());
            row.setGroupName(c.getGroup() != null ? c.getGroup().getGroupName() : null);
            row.setForceUnblocked(c.isForceUnblocked());

            if (isStatement) {
                long unpaidCount = statementRepository.countUnpaidStatements(c.getId());
                BigDecimal unpaidAmount = statementRepository.sumUnpaidStatementBalance(c.getId());
                java.time.LocalDate oldest = statementRepository.findOldestUnpaidStatementDate(c.getId());

                if (unpaidCount == 0) continue; // Skip customers with no unpaid statements

                long daysOverdue = oldest != null ? ChronoUnit.DAYS.between(oldest, java.time.LocalDate.now()) : 0;
                boolean overdue = c.getRepaymentDays() != null && daysOverdue > c.getRepaymentDays();

                row.setUnpaidCount(unpaidCount);
                row.setUnpaidAmount(unpaidAmount);
                row.setOldestUnpaidDate(oldest != null ? oldest.toString() : null);
                row.setDaysOverdue(daysOverdue);
                row.setOverdue(overdue);

                statementRows.add(row);
                totalStatementAmount = totalStatementAmount.add(unpaidAmount);
                if (overdue) totalStatementOverdue++;
            } else {
                long unpaidCount = invoiceBillRepository.countUnpaidLocalCreditBills(c.getId());
                BigDecimal unpaidAmount = invoiceBillRepository.sumUnpaidLocalCreditAmount(c.getId());
                LocalDateTime oldest = invoiceBillRepository.findOldestUnpaidLocalBillDate(c.getId());

                if (unpaidCount == 0) continue; // Skip customers with no unpaid bills

                long daysOverdue = oldest != null ? ChronoUnit.DAYS.between(oldest.toLocalDate(), java.time.LocalDate.now()) : 0;
                boolean overdue = c.getRepaymentDays() != null && daysOverdue > c.getRepaymentDays();

                row.setUnpaidCount(unpaidCount);
                row.setUnpaidAmount(unpaidAmount);
                row.setOldestUnpaidDate(oldest != null ? oldest.toLocalDate().toString() : null);
                row.setDaysOverdue(daysOverdue);
                row.setOverdue(overdue);

                localRows.add(row);
                totalLocalAmount = totalLocalAmount.add(unpaidAmount);
                if (overdue) totalLocalOverdue++;
            }
        }

        // Sort: overdue first, then by daysOverdue descending
        localRows.sort((a, b) -> {
            if (a.isOverdue() != b.isOverdue()) return a.isOverdue() ? -1 : 1;
            return Long.compare(b.getDaysOverdue(), a.getDaysOverdue());
        });
        statementRows.sort((a, b) -> {
            if (a.isOverdue() != b.isOverdue()) return a.isOverdue() ? -1 : 1;
            return Long.compare(b.getDaysOverdue(), a.getDaysOverdue());
        });

        CreditMonitoringDashboard dashboard = new CreditMonitoringDashboard();
        dashboard.setLocalCustomers(localRows);
        dashboard.setStatementCustomers(statementRows);
        dashboard.setTotalLocalOverdue(totalLocalOverdue);
        dashboard.setTotalStatementOverdue(totalStatementOverdue);
        dashboard.setTotalLocalAmount(totalLocalAmount);
        dashboard.setTotalStatementAmount(totalStatementAmount);
        return dashboard;
    }

    // ─── Bubble Map ────────────────────────────────────────────────

    private static final int[][] BAND_RANGES = {{0,7},{8,14},{15,30},{31,60},{61,90},{91,99999}};
    private static final String[] BAND_LABELS = {"0–7 days","8–14 days","15–30 days","31–60 days","61–90 days","90+ days"};
    private static final String[] BAND_COLORS = {"#10B981","#F59E0B","#8B5CF6","#EC4899","#EF4444","#DC2626"};

    @Transactional(readOnly = true)
    public BubbleMapData getBubbleMapData(String type) {
        Long scid = SecurityUtils.getScid();
        List<Customer> allCustomers = customerRepository.findAllByScid(scid);

        // Initialize bands
        List<BubbleMapBand> bands = new ArrayList<>();
        for (int i = 0; i < BAND_RANGES.length; i++) {
            BubbleMapBand band = new BubbleMapBand();
            band.setLabel(BAND_LABELS[i]);
            band.setMin(BAND_RANGES[i][0]);
            band.setMax(BAND_RANGES[i][1]);
            band.setColor(BAND_COLORS[i]);
            band.setCustomers(new ArrayList<>());
            bands.add(band);
        }

        int totalCustomers = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Customer c : allCustomers) {
            if (c.getCreditLimitAmount() == null || c.getCreditLimitAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean isStatement = c.getStatementFrequency() != null && !c.getStatementFrequency().isBlank();

            // Filter by type
            if ("local".equals(type) && isStatement) continue;
            if ("statement".equals(type) && !isStatement) continue;

            long daysOverdue;
            long unpaidCount;
            BigDecimal unpaidAmount;

            if (isStatement) {
                java.time.LocalDate oldest = statementRepository.findOldestUnpaidStatementDate(c.getId());
                if (oldest == null) continue;
                daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(oldest, java.time.LocalDate.now());
                unpaidCount = statementRepository.countUnpaidStatements(c.getId());
                unpaidAmount = statementRepository.sumUnpaidStatementBalance(c.getId());
            } else {
                LocalDateTime oldest = invoiceBillRepository.findOldestUnpaidLocalBillDate(c.getId());
                if (oldest == null) continue;
                daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(oldest.toLocalDate(), java.time.LocalDate.now());
                unpaidCount = invoiceBillRepository.countUnpaidLocalCreditBills(c.getId());
                unpaidAmount = invoiceBillRepository.sumUnpaidLocalCreditAmount(c.getId());
            }

            if (unpaidCount == 0) continue;

            boolean skipped = false;
            try { skipped = invoiceBillRepository.hasSkippedBills(c.getId()); } catch (Exception ignored) {}

            BubbleCustomer bc = new BubbleCustomer();
            bc.setCustomerId(c.getId());
            bc.setCustomerName(c.getName());
            bc.setDaysOverdue(daysOverdue);
            bc.setUnpaidAmount(unpaidAmount);
            bc.setUnpaidCount(unpaidCount);
            bc.setRepaymentDays(c.getRepaymentDays());
            bc.setStatus(c.getStatus() != null ? c.getStatus().name() : "ACTIVE");
            bc.setHasSkippedBills(skipped);
            bc.setGroupName(c.getGroup() != null ? c.getGroup().getGroupName() : null);

            // Place into correct band
            for (BubbleMapBand band : bands) {
                if (daysOverdue >= band.getMin() && daysOverdue <= band.getMax()) {
                    band.getCustomers().add(bc);
                    break;
                }
            }

            totalCustomers++;
            totalAmount = totalAmount.add(unpaidAmount);
        }

        BubbleMapData data = new BubbleMapData();
        data.setBands(bands);
        data.setTotalCustomers(totalCustomers);
        data.setTotalOverdueAmount(totalAmount);
        return data;
    }

    // ─── DTOs ─────────────────────────────────────────────────────

    @Getter @Setter
    public static class CreditHealth {
        private Long customerId;
        private String customerName;
        private String status;
        private String riskLevel;        // HIGH, MEDIUM, LOW
        private BigDecimal creditLimit;
        private BigDecimal ledgerBalance;
        private BigDecimal totalBilled;
        private BigDecimal totalPaid;
        private BigDecimal utilizationPercent;
        private long oldestUnpaidDays;
        private String suggestedAction;
        private int blockCount;
        private LocalDateTime lastBlockedAt;
        private String policyName;
        private String categoryType;      // GOVERNMENT, NON_GOVERNMENT
        private String categoryName;
        private String groupName;
        private String statementFrequency; // MONTHLY, BIWEEKLY, etc. (null = Local/credit)
    }

    @Getter @Setter
    public static class ReconciliationSummary {
        private CreditHealth health;
        private BigDecimal creditLimitLiters;
        private BigDecimal consumedLiters;
        private String statementFrequency;
        private String statementGrouping;
        private String categoryType;
        private String categoryName;
        private String groupName;
        private List<BlockEventDTO> blockHistory;
    }

    @Getter @Setter
    public static class BlockEventDTO {
        private Long id;
        private String eventType;
        private String triggerType;
        private String reason;
        private String notes;
        private String performedByName;
        private String previousStatus;
        private LocalDateTime createdAt;
    }

    @Getter @Setter
    public static class BubbleMapData {
        private List<BubbleMapBand> bands = new ArrayList<>();
        private int totalCustomers;
        private BigDecimal totalOverdueAmount = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class BubbleMapBand {
        private String label;
        private int min;
        private int max;
        private String color;
        private List<BubbleCustomer> customers = new ArrayList<>();
    }

    @Getter @Setter
    public static class BubbleCustomer {
        private Long customerId;
        private String customerName;
        private long daysOverdue;
        private BigDecimal unpaidAmount;
        private long unpaidCount;
        private Integer repaymentDays;
        private String status;
        private boolean hasSkippedBills;
        private String groupName;
    }

    @Getter @Setter
    public static class CreditMonitoringDashboard {
        private List<CreditCustomerRow> localCustomers = new ArrayList<>();
        private List<CreditCustomerRow> statementCustomers = new ArrayList<>();
        private int totalLocalOverdue;
        private int totalStatementOverdue;
        private BigDecimal totalLocalAmount = BigDecimal.ZERO;
        private BigDecimal totalStatementAmount = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class CreditCustomerRow {
        private Long customerId;
        private String customerName;
        private String status;
        private Integer repaymentDays;
        private BigDecimal creditLimit;
        private String groupName;
        private boolean forceUnblocked;
        private long unpaidCount;
        private BigDecimal unpaidAmount;
        private String oldestUnpaidDate;
        private long daysOverdue;
        private boolean overdue;
    }
}
