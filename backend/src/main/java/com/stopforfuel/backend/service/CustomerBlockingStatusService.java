package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.BlockingStatusResponse;
import com.stopforfuel.backend.entity.CreditPolicy;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only aggregator that evaluates every gate an invoice must pass through
 * for a given customer (and optional vehicle + tentative invoice amount/liters),
 * so the cashier sees the exact blocking reason instead of a generic toast.
 *
 * Does not mutate state — all enforcement remains in InvoiceBillService and
 * CreditMonitoringService. This service only reports what those would decide.
 */
@Service
@RequiredArgsConstructor
public class CustomerBlockingStatusService {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;
    private final CreditPolicyService creditPolicyService;

    @Transactional(readOnly = true)
    public BlockingStatusResponse evaluate(Long customerId,
                                           Long vehicleId,
                                           BigDecimal invoiceAmount,
                                           BigDecimal invoiceLiters) {
        Customer customer = customerRepository.findByIdAndScid(customerId, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        CreditPolicy policy = creditPolicyService.getEffectivePolicy(customer);
        List<BlockingStatusResponse.Gate> gates = new ArrayList<>();

        gates.add(evaluateCustomerStatus(customer));
        gates.add(evaluateCreditAmount(customer, invoiceAmount));
        gates.add(evaluateCreditLiters(customer, invoiceLiters));
        gates.add(evaluateAging(customer, policy));

        Vehicle vehicle = vehicleId != null ? vehicleRepository.findById(vehicleId).orElse(null) : null;
        gates.add(evaluateVehicleStatus(vehicle));
        gates.add(evaluateVehicleMonthlyLiters(vehicle, invoiceLiters));

        String overall = computeOverall(gates, customer.isForceUnblocked());
        String primaryReason = buildPrimaryReason(gates, customer.isForceUnblocked());
        String suggestedAction = buildSuggestedAction(gates, customer.isForceUnblocked());

        return BlockingStatusResponse.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .overall(overall)
                .forceUnblocked(customer.isForceUnblocked())
                .primaryReason(primaryReason)
                .suggestedAction(suggestedAction)
                .gates(gates)
                .build();
    }

    // ─── Gate evaluators ──────────────────────────────────────────

    private BlockingStatusResponse.Gate evaluateCustomerStatus(Customer customer) {
        EntityStatus status = customer.getStatus();
        String statusName = status != null ? status.name() : "ACTIVE";
        String state;
        String detail;
        if (status == EntityStatus.BLOCKED) {
            state = "FAIL";
            detail = "Customer is manually or auto-blocked";
        } else if (status == EntityStatus.INACTIVE) {
            state = "FAIL";
            detail = "Customer is inactive";
        } else {
            state = "PASS";
            detail = "Account is active";
        }
        return BlockingStatusResponse.Gate.builder()
                .key("CUSTOMER_STATUS")
                .label("Customer Status")
                .state(state)
                .value(statusName)
                .limit(null)
                .detail(detail)
                .progressPercent(null)
                .build();
    }

    private BlockingStatusResponse.Gate evaluateCreditAmount(Customer customer, BigDecimal invoiceAmount) {
        BigDecimal limit = customer.getCreditLimitAmount();
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return BlockingStatusResponse.Gate.builder()
                    .key("CREDIT_AMOUNT")
                    .label("Credit (\u20b9)")
                    .state("SKIPPED")
                    .detail("No credit amount limit set")
                    .build();
        }

        BigDecimal unbilled = invoiceBillRepository.sumUnbilledCreditByCustomer(customer.getId());
        BigDecimal projected = unbilled.add(invoiceAmount != null ? invoiceAmount : BigDecimal.ZERO);
        BigDecimal percent = projected.multiply(new BigDecimal(100))
                .divide(limit, 0, RoundingMode.HALF_UP);

        String state;
        String detail;
        if (projected.compareTo(limit) > 0) {
            state = "FAIL";
            BigDecimal over = projected.subtract(limit);
            detail = invoiceAmount != null && invoiceAmount.compareTo(BigDecimal.ZERO) > 0
                    ? "Unbilled \u20b9" + unbilled.toPlainString() + " + this bill \u20b9" + invoiceAmount.toPlainString()
                    + " exceeds limit by \u20b9" + over.toPlainString()
                    : "Unbilled \u20b9" + unbilled.toPlainString() + " exceeds limit by \u20b9" + over.toPlainString();
        } else if (percent.intValue() >= 80) {
            state = "WARN";
            detail = percent.intValue() + "% of credit limit used";
        } else {
            state = "PASS";
            detail = "Within credit limit";
        }

        return BlockingStatusResponse.Gate.builder()
                .key("CREDIT_AMOUNT")
                .label("Credit (\u20b9)")
                .state(state)
                .value(projected)
                .limit(limit)
                .detail(detail)
                .progressPercent(percent.intValue())
                .build();
    }

    private BlockingStatusResponse.Gate evaluateCreditLiters(Customer customer, BigDecimal invoiceLiters) {
        BigDecimal limit = customer.getCreditLimitLiters();
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return BlockingStatusResponse.Gate.builder()
                    .key("CREDIT_LITERS")
                    .label("Liter Credit")
                    .state("SKIPPED")
                    .detail("No liter limit set")
                    .build();
        }

        BigDecimal consumed = customer.getConsumedLiters() != null ? customer.getConsumedLiters() : BigDecimal.ZERO;
        BigDecimal projected = consumed.add(invoiceLiters != null ? invoiceLiters : BigDecimal.ZERO);
        BigDecimal percent = projected.multiply(new BigDecimal(100))
                .divide(limit, 0, RoundingMode.HALF_UP);

        String state;
        String detail;
        if (projected.compareTo(limit) > 0) {
            state = "FAIL";
            BigDecimal over = projected.subtract(limit);
            detail = "Liter limit exceeded by " + over.toPlainString() + " L";
        } else if (percent.intValue() >= 80) {
            state = "WARN";
            detail = percent.intValue() + "% of liter limit used";
        } else {
            state = "PASS";
            detail = "Within liter limit";
        }

        return BlockingStatusResponse.Gate.builder()
                .key("CREDIT_LITERS")
                .label("Liter Credit")
                .state(state)
                .value(projected)
                .limit(limit)
                .detail(detail)
                .progressPercent(percent.intValue())
                .build();
    }

    private BlockingStatusResponse.Gate evaluateAging(Customer customer, CreditPolicy policy) {
        long daysOverdue = 0;
        boolean hasUnpaid = false;
        boolean isStatementCustomer = customer.getParty() != null
                && "Statement".equalsIgnoreCase(customer.getParty().getPartyType());

        if (isStatementCustomer) {
            LocalDate oldest = statementRepository.findOldestUnpaidStatementDate(customer.getId());
            if (oldest != null) {
                hasUnpaid = true;
                daysOverdue = ChronoUnit.DAYS.between(oldest, LocalDate.now());
            }
        } else {
            LocalDateTime oldest = invoiceBillRepository.findOldestUnpaidLocalBillDate(customer.getId());
            if (oldest != null) {
                hasUnpaid = true;
                daysOverdue = ChronoUnit.DAYS.between(oldest.toLocalDate(), LocalDate.now());
            }
        }

        Integer blockDays = customer.getRepaymentDays() != null && customer.getRepaymentDays() > 0
                ? customer.getRepaymentDays()
                : policy.getAgingBlockDays();
        Integer warnDays = policy.getAgingWatchDays();

        String state;
        String detail;
        Integer percent = null;

        if (!hasUnpaid) {
            state = "PASS";
            detail = "No unpaid bills";
        } else {
            if (blockDays != null && blockDays > 0) {
                percent = (int) Math.min(200, Math.round(daysOverdue * 100.0 / blockDays));
            }
            if (blockDays != null && daysOverdue >= blockDays) {
                state = "FAIL";
                detail = "Oldest unpaid bill is " + daysOverdue + " days old (limit " + blockDays + " days)";
            } else if (warnDays != null && daysOverdue >= warnDays) {
                state = "WARN";
                detail = "Oldest unpaid bill is " + daysOverdue + " days old";
            } else {
                state = "PASS";
                detail = "Oldest unpaid bill is " + daysOverdue + " days old";
            }
        }

        return BlockingStatusResponse.Gate.builder()
                .key("AGING")
                .label("Oldest Unpaid Bill")
                .state(state)
                .value(hasUnpaid ? daysOverdue : 0)
                .limit(blockDays)
                .detail(detail)
                .progressPercent(percent)
                .build();
    }

    private BlockingStatusResponse.Gate evaluateVehicleStatus(Vehicle vehicle) {
        if (vehicle == null) {
            return BlockingStatusResponse.Gate.builder()
                    .key("VEHICLE_STATUS")
                    .label("Vehicle Status")
                    .state("SKIPPED")
                    .detail("No vehicle selected")
                    .build();
        }
        EntityStatus status = vehicle.getStatus();
        String statusName = status != null ? status.name() : "ACTIVE";
        if (status == EntityStatus.BLOCKED) {
            return BlockingStatusResponse.Gate.builder()
                    .key("VEHICLE_STATUS")
                    .label("Vehicle Status")
                    .state("FAIL")
                    .value(statusName)
                    .detail("Vehicle " + vehicle.getVehicleNumber() + " is blocked")
                    .build();
        }
        if (status == EntityStatus.INACTIVE) {
            return BlockingStatusResponse.Gate.builder()
                    .key("VEHICLE_STATUS")
                    .label("Vehicle Status")
                    .state("FAIL")
                    .value(statusName)
                    .detail("Vehicle " + vehicle.getVehicleNumber() + " is inactive")
                    .build();
        }
        return BlockingStatusResponse.Gate.builder()
                .key("VEHICLE_STATUS")
                .label("Vehicle Status")
                .state("PASS")
                .value(statusName)
                .detail("Vehicle is active")
                .build();
    }

    private BlockingStatusResponse.Gate evaluateVehicleMonthlyLiters(Vehicle vehicle, BigDecimal invoiceLiters) {
        if (vehicle == null) {
            return BlockingStatusResponse.Gate.builder()
                    .key("VEHICLE_MONTHLY_LITERS")
                    .label("Vehicle Monthly Liters")
                    .state("SKIPPED")
                    .detail("No vehicle selected")
                    .build();
        }
        BigDecimal limit = vehicle.getMaxLitersPerMonth();
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return BlockingStatusResponse.Gate.builder()
                    .key("VEHICLE_MONTHLY_LITERS")
                    .label("Vehicle Monthly Liters")
                    .state("SKIPPED")
                    .detail("No monthly liter limit on this vehicle")
                    .build();
        }
        BigDecimal consumed = vehicle.getConsumedLiters() != null ? vehicle.getConsumedLiters() : BigDecimal.ZERO;
        BigDecimal projected = consumed.add(invoiceLiters != null ? invoiceLiters : BigDecimal.ZERO);
        BigDecimal percent = projected.multiply(new BigDecimal(100))
                .divide(limit, 0, RoundingMode.HALF_UP);

        String state;
        String detail;
        if (projected.compareTo(limit) > 0) {
            state = "FAIL";
            BigDecimal over = projected.subtract(limit);
            detail = "Vehicle monthly limit exceeded by " + over.toPlainString() + " L";
        } else if (percent.intValue() >= 80) {
            state = "WARN";
            detail = percent.intValue() + "% of vehicle monthly limit used";
        } else {
            state = "PASS";
            detail = "Within vehicle monthly limit";
        }
        return BlockingStatusResponse.Gate.builder()
                .key("VEHICLE_MONTHLY_LITERS")
                .label("Vehicle Monthly Liters")
                .state(state)
                .value(projected)
                .limit(limit)
                .detail(detail)
                .progressPercent(percent.intValue())
                .build();
    }

    // ─── Aggregation helpers ──────────────────────────────────────

    private String computeOverall(List<BlockingStatusResponse.Gate> gates, boolean forceUnblocked) {
        boolean anyFail = gates.stream().anyMatch(g -> "FAIL".equals(g.getState()));
        boolean anyWarn = gates.stream().anyMatch(g -> "WARN".equals(g.getState()));
        if (forceUnblocked && anyFail) return "OVERRIDE";
        if (anyFail) return "BLOCKED";
        if (anyWarn) return "WARN";
        return "PASS";
    }

    private String buildPrimaryReason(List<BlockingStatusResponse.Gate> gates, boolean forceUnblocked) {
        List<String> reasons = new ArrayList<>();
        for (var g : gates) {
            if ("FAIL".equals(g.getState())) reasons.add(g.getDetail());
            if (reasons.size() == 2) break;
        }
        if (reasons.isEmpty()) {
            boolean anyWarn = gates.stream().anyMatch(g -> "WARN".equals(g.getState()));
            return anyWarn ? "Approaching a credit threshold" : "All checks passing";
        }
        String joined = String.join(" and ", reasons);
        return forceUnblocked ? joined + " (force-unblock override active)" : joined;
    }

    private String buildSuggestedAction(List<BlockingStatusResponse.Gate> gates, boolean forceUnblocked) {
        if (forceUnblocked) {
            return "Invoice allowed via force-unblock (owner override)";
        }
        for (var g : gates) {
            if (!"FAIL".equals(g.getState())) continue;
            switch (g.getKey()) {
                case "CUSTOMER_STATUS":
                    return "Admin must unblock the customer before invoicing";
                case "CREDIT_AMOUNT":
                    return "Collect a payment to reduce unbilled credit, or raise the credit limit";
                case "CREDIT_LITERS":
                    return "Reset the period or raise the liter limit";
                case "AGING":
                    return "Collect payment on the oldest unpaid bill";
                case "VEHICLE_STATUS":
                    return "Admin must unblock the vehicle, or pick another vehicle";
                case "VEHICLE_MONTHLY_LITERS":
                    return "Pick another vehicle, or wait for next month's reset";
                default:
                    return "Resolve the failing check";
            }
        }
        return "No action needed";
    }
}
