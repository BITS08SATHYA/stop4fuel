package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerPortalService {

    private final CustomerService customerService;
    private final StatementService statementService;
    private final PaymentService paymentService;
    private final InvoiceBillService invoiceBillService;
    private final VehicleService vehicleService;
    private final InvoiceBillRepository invoiceBillRepository;

    @Transactional(readOnly = true)
    public CustomerDashboardData getDashboard(Long customerId) {
        CustomerDashboardData data = new CustomerDashboardData();

        // Credit info
        Map<String, Object> creditInfo = customerService.getCreditInfo(customerId);
        data.setCreditLimitAmount(toBigDecimal(creditInfo.get("creditLimitAmount")));
        data.setCreditLimitLiters(toBigDecimal(creditInfo.get("creditLimitLiters")));
        data.setConsumedLiters(toBigDecimal(creditInfo.get("consumedLiters")));
        data.setLedgerBalance(toBigDecimal(creditInfo.get("ledgerBalance")));
        data.setTotalBilled(toBigDecimal(creditInfo.get("totalBilled")));
        data.setTotalPaid(toBigDecimal(creditInfo.get("totalPaid")));
        data.setOutstandingBalance(toBigDecimal(creditInfo.get("totalBilled"))
                .subtract(toBigDecimal(creditInfo.get("totalPaid"))));

        // Credit utilization
        BigDecimal creditLimit = data.getCreditLimitAmount();
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            data.setCreditUtilization(data.getOutstandingBalance()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(creditLimit, 1, RoundingMode.HALF_UP));
        } else {
            data.setCreditUtilization(BigDecimal.ZERO);
        }

        // Outstanding statements
        List<Statement> outstanding = statementService.getOutstandingByCustomer(customerId);
        data.setUnpaidStatements(outstanding.size());
        data.setUnpaidStatementsAmount(outstanding.stream()
                .map(s -> s.getBalanceAmount() != null ? s.getBalanceAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // Vehicle count
        data.setVehicleCount(vehicleService.getVehiclesByCustomerId(customerId).size());

        // Recent invoices (last 5)
        Page<InvoiceBill> recentInvoices = invoiceBillService.getInvoicesByCustomer(
                customerId, null, null, null, null,
                Pageable.ofSize(5));
        data.setRecentInvoices(recentInvoices.getContent().stream().map(inv -> {
            RecentInvoiceItem item = new RecentInvoiceItem();
            item.setId(inv.getId());
            item.setDate(inv.getDate() != null ? inv.getDate().toString() : null);
            item.setBillNo(inv.getBillNo());
            item.setBillType(inv.getBillType() != null ? inv.getBillType().name() : null);
            item.setNetAmount(inv.getNetAmount());
            item.setPaymentStatus(inv.getPaymentStatus() != null ? inv.getPaymentStatus().name() : null);
            item.setVehicleNumber(inv.getVehicle() != null ? inv.getVehicle().getVehicleNumber() : null);
            return item;
        }).toList());

        // Recent payments (last 5)
        Page<Payment> recentPayments = paymentService.getPaymentsByCustomer(customerId, Pageable.ofSize(5));
        data.setRecentPayments(recentPayments.getContent().stream().map(p -> {
            RecentPaymentItem item = new RecentPaymentItem();
            item.setId(p.getId());
            item.setDate(p.getPaymentDate() != null ? p.getPaymentDate().toString() : null);
            item.setAmount(p.getAmount());
            item.setPaymentMode(p.getPaymentMode() != null ? p.getPaymentMode().getModeName() : null);
            item.setReferenceNo(p.getReferenceNo());
            return item;
        }).toList());

        return data;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    // --- DTOs ---

    @Getter @Setter @NoArgsConstructor
    public static class CustomerDashboardData {
        private BigDecimal creditLimitAmount;
        private BigDecimal creditLimitLiters;
        private BigDecimal consumedLiters;
        private BigDecimal ledgerBalance;
        private BigDecimal totalBilled;
        private BigDecimal totalPaid;
        private BigDecimal outstandingBalance;
        private BigDecimal creditUtilization;
        private int unpaidStatements;
        private BigDecimal unpaidStatementsAmount;
        private int vehicleCount;
        private List<RecentInvoiceItem> recentInvoices;
        private List<RecentPaymentItem> recentPayments;
    }

    @Getter @Setter @NoArgsConstructor
    public static class RecentInvoiceItem {
        private Long id;
        private String date;
        private String billNo;
        private String billType;
        private BigDecimal netAmount;
        private String paymentStatus;
        private String vehicleNumber;
    }

    @Getter @Setter @NoArgsConstructor
    public static class RecentPaymentItem {
        private Long id;
        private String date;
        private BigDecimal amount;
        private String paymentMode;
        private String referenceNo;
    }
}
