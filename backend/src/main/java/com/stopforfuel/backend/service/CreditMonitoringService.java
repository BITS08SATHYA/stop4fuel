package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ManualScanResult;
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
        int totalUnblocked = 0;

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

            List<Customer> blockedCustomers = customerRepository.findByStatusAndScid(EntityStatus.BLOCKED, scid);
            for (Customer customer : blockedCustomers) {
                if (tryAutoUnblock(customer, "AUTO_RESOLVED")) {
                    totalUnblocked++;
                }
            }
        }

        log.info("Daily auto-block scan complete. Blocked {}, auto-unblocked {}.", totalBlocked, totalUnblocked);
    }

    /**
     * Manual trigger for the auto-block scan (admin use).
     * Runs for the current tenant only. Returns a per-customer breakdown so the
     * UI can show which customers were blocked vs. passed vs. skipped.
     */
    @Transactional
    public ManualScanResult runManualScan() {
        Long scid = SecurityUtils.getScid();
        List<Customer> activeCustomers = customerRepository.findByStatusAndScid(EntityStatus.ACTIVE, scid);
        ManualScanResult result = new ManualScanResult();

        for (Customer customer : activeCustomers) {
            ManualScanResult.ScanEntry entry = new ManualScanResult.ScanEntry();
            entry.setCustomerId(customer.getId());
            entry.setCustomerName(customer.getName());
            entry.setPartyType(customer.getParty() != null ? customer.getParty().getPartyType() : null);
            result.getEntries().add(entry);
            result.setScannedCount(result.getScannedCount() + 1);

            if (customer.getCreditLimitAmount() == null
                    || customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) == 0) {
                entry.setOutcome("SKIPPED");
                entry.setReason("No credit limit set");
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }

            CreditPolicy policy = creditPolicyService.getEffectivePolicy(customer);
            if (!Boolean.TRUE.equals(policy.getAutoBlockEnabled())) {
                entry.setOutcome("SKIPPED");
                entry.setReason("Policy auto-block disabled");
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }

            // Populate diagnostics regardless of outcome (for the UI table)
            BigDecimal totalBilled = invoiceBillRepository.sumAllCreditBillsByCustomer(customer.getId());
            BigDecimal totalPaid = paymentRepository.sumAllPaymentsByCustomer(customer.getId());
            BigDecimal ledgerBalance = totalBilled.subtract(totalPaid);
            if (customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
                entry.setUtilizationPercent(ledgerBalance.multiply(new BigDecimal(100))
                        .divide(customer.getCreditLimitAmount(), 2, RoundingMode.HALF_UP));
            }
            Optional<LocalDateTime> oldest = invoiceBillRepository.findOldestUnpaidBillDate(customer.getId());
            oldest.ifPresent(d -> entry.setOldestUnpaidDays(ChronoUnit.DAYS.between(d.toLocalDate(), java.time.LocalDate.now())));

            String blockReason = evaluateBlockTriggers(customer, policy);
            if (blockReason != null) {
                blockCustomerWithEvent(customer, "AUTO_SCHEDULED", blockReason, null);
                entry.setOutcome("BLOCKED");
                entry.setReason(blockReason);
                result.setBlockedCount(result.getBlockedCount() + 1);
            } else {
                entry.setOutcome("PASS");
                entry.setReason(null);
            }
        }

        // Symmetric pass: any currently-BLOCKED customer whose triggers have
        // since cleared gets auto-unblocked. Skips manual admin blocks.
        List<Customer> blockedCustomers = customerRepository.findByStatusAndScid(EntityStatus.BLOCKED, scid);
        for (Customer customer : blockedCustomers) {
            if (tryAutoUnblock(customer, "AUTO_RESOLVED")) {
                ManualScanResult.ScanEntry entry = new ManualScanResult.ScanEntry();
                entry.setCustomerId(customer.getId());
                entry.setCustomerName(customer.getName());
                entry.setPartyType(customer.getParty() != null ? customer.getParty().getPartyType() : null);
                entry.setOutcome("UNBLOCKED");
                entry.setReason("All credit triggers cleared");
                result.getEntries().add(entry);
                result.setUnblockedCount(result.getUnblockedCount() + 1);
            }
        }

        return result;
    }

    /**
     * Tries to auto-unblock one customer. Returns true if the status was flipped
     * back to ACTIVE. Skips when:
     *  - customer is not currently BLOCKED
     *  - the most-recent BLOCKED event in the audit log is MANUAL (admin block —
     *    must stay sticky so the system never undoes a deliberate admin decision)
     *  - any auto-block trigger is still firing
     */
    @Transactional
    public boolean tryAutoUnblock(Customer customer, String triggerType) {
        if (customer.getStatus() != EntityStatus.BLOCKED) return false;

        // Find the most recent BLOCKED event; protect MANUAL blocks but only when
        // they are still the latest event (a later UNBLOCKED followed by a silent
        // auto-block from the legacy InvoiceBillService path would otherwise be
        // mis-classified as manual).
        List<CustomerBlockEvent> events = blockEventRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        var latestBlocked = events.stream()
                .filter(e -> "BLOCKED".equals(e.getEventType()))
                .findFirst();
        if (latestBlocked.isPresent() && "MANUAL".equals(latestBlocked.get().getTriggerType())) {
            boolean stillCurrent = !events.isEmpty()
                    && events.get(0).getId().equals(latestBlocked.get().getId());
            if (stillCurrent) return false;
        }

        if (customer.getCreditLimitAmount() == null
                || customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) == 0) {
            // Nothing to evaluate — refuse to auto-unblock; an admin must intervene.
            return false;
        }

        CreditPolicy policy = creditPolicyService.getEffectivePolicy(customer);
        if (evaluateBlockTriggers(customer, policy) != null) return false;

        unblockCustomerWithEvent(customer, triggerType,
                "Auto-unblocked: all credit triggers cleared");
        return true;
    }

    /**
     * Sets status back to ACTIVE and writes an UNBLOCKED audit event.
     * Mirror of blockCustomerWithEvent — never touches forceUnblocked or blockCount.
     */
    @Transactional
    public void unblockCustomerWithEvent(Customer customer, String triggerType, String reason) {
        customer.setStatus(EntityStatus.ACTIVE);
        customerRepository.save(customer);

        CustomerBlockEvent event = new CustomerBlockEvent();
        event.setCustomer(customer);
        event.setScid(customer.getScid());
        event.setEventType("UNBLOCKED");
        event.setTriggerType(triggerType);
        event.setReason(reason);
        event.setPreviousStatus("BLOCKED");
        blockEventRepository.save(event);
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

            boolean isStatement = c.getParty() != null && "Statement".equalsIgnoreCase(c.getParty().getPartyType());

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

    // ─── Customer Unpaid Bills (Side Panel) ────────────────────────

    @Transactional(readOnly = true)
    public CustomerUnpaidDetail getCustomerUnpaidDetail(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new com.stopforfuel.backend.exception.ResourceNotFoundException("Customer not found"));

        CustomerUnpaidDetail detail = new CustomerUnpaidDetail();
        detail.setCustomerId(customer.getId());
        detail.setCustomerName(customer.getName());
        detail.setGroupName(customer.getGroup() != null ? customer.getGroup().getGroupName() : null);
        detail.setStatus(customer.getStatus() != null ? customer.getStatus().name() : "ACTIVE");
        detail.setPartyType(customer.getParty() != null ? customer.getParty().getPartyType() : "Local");
        detail.setRepaymentDays(customer.getRepaymentDays());
        detail.setCreditLimit(customer.getCreditLimitAmount());

        boolean isStatement = customer.getParty() != null && "Statement".equalsIgnoreCase(customer.getParty().getPartyType());

        if (isStatement) {
            // Get unpaid statements
            List<com.stopforfuel.backend.entity.Statement> statements = statementRepository.findByCustomerIdAndStatus(customerId, "NOT_PAID");
            List<UnpaidItem> items = new ArrayList<>();
            for (var s : statements) {
                UnpaidItem item = new UnpaidItem();
                item.setId(s.getId());
                item.setReference(s.getStatementNo());
                item.setDate(s.getStatementDate() != null ? s.getStatementDate().toString() : null);
                item.setAmount(s.getBalanceAmount());
                item.setBillCount(s.getNumberOfBills());
                long days = s.getStatementDate() != null ? java.time.temporal.ChronoUnit.DAYS.between(s.getStatementDate(), java.time.LocalDate.now()) : 0;
                item.setDaysOld(days);
                items.add(item);
            }
            items.sort((a, b) -> Long.compare(b.getDaysOld(), a.getDaysOld()));
            detail.setUnpaidItems(items);
            detail.setTotalUnpaid(items.stream().map(i -> i.getAmount() != null ? i.getAmount() : java.math.BigDecimal.ZERO).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        } else {
            // Get unpaid local credit bills — aligns with bubble-map aggregate so
            // the detail panel shows the same rows that put the customer into its bucket.
            var bills = invoiceBillRepository.findUnpaidLocalCreditByCustomer(
                    customerId, org.springframework.data.domain.PageRequest.of(0, 500));
            List<UnpaidItem> items = new ArrayList<>();
            for (var b : bills.getContent()) {
                UnpaidItem item = new UnpaidItem();
                item.setId(b.getId());
                item.setReference(b.getBillNo());
                item.setDate(b.getDate() != null ? b.getDate().toLocalDate().toString() : null);
                // PARTIAL bills: show the outstanding balance (net - payments) rather than the face value.
                BigDecimal net = b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
                BigDecimal paid = b.getPaymentStatus() == com.stopforfuel.backend.enums.PaymentStatus.PARTIAL
                        ? paymentRepository.sumPaymentsByInvoiceBillId(b.getId())
                        : BigDecimal.ZERO;
                item.setAmount(net.subtract(paid != null ? paid : BigDecimal.ZERO));
                item.setVehicleNo(b.getVehicle() != null ? b.getVehicle().getVehicleNumber() : b.getBillDesc());
                long days = b.getDate() != null ? java.time.temporal.ChronoUnit.DAYS.between(b.getDate().toLocalDate(), java.time.LocalDate.now()) : 0;
                item.setDaysOld(days);
                items.add(item);
            }
            detail.setUnpaidItems(items);
            detail.setTotalUnpaid(items.stream().map(i -> i.getAmount() != null ? i.getAmount() : java.math.BigDecimal.ZERO).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        }

        return detail;
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
            boolean isStatement = c.getParty() != null && "Statement".equalsIgnoreCase(c.getParty().getPartyType());

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
            bc.setPartyType(c.getParty() != null ? c.getParty().getPartyType() : "Local");

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
    public static class CustomerUnpaidDetail {
        private Long customerId;
        private String customerName;
        private String groupName;
        private String status;
        private String partyType;
        private Integer repaymentDays;
        private BigDecimal creditLimit;
        private BigDecimal totalUnpaid;
        private List<UnpaidItem> unpaidItems = new ArrayList<>();
    }

    @Getter @Setter
    public static class UnpaidItem {
        private Long id;
        private String reference; // billNo or statementNo
        private String date;
        private BigDecimal amount;
        private String vehicleNo;
        private Integer billCount; // for statements
        private long daysOld;
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
        private String partyType;
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
