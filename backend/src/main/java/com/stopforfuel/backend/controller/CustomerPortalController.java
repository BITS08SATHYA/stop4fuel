package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.StatementDTO;
import com.stopforfuel.backend.dto.PaymentDTO;
import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.dto.VehicleDTO;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.service.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer-portal")
@RequiredArgsConstructor
public class CustomerPortalController {

    private final CustomerPortalService customerPortalService;
    private final StatementService statementService;
    private final PaymentService paymentService;
    private final InvoiceBillService invoiceBillService;
    private final VehicleService vehicleService;
    private final CustomerService customerService;

    private Long getCustomerId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userId;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerPortalService.CustomerDashboardData getDashboard() {
        return customerPortalService.getDashboard(getCustomerId());
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> getProfile() {
        return customerService.getCreditInfo(getCustomerId());
    }

    @GetMapping("/statements")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<StatementDTO> getStatements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return statementService.getStatements(
                getCustomerId(), status, null, fromDate, toDate, null,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "statementDate"))
        ).map(StatementDTO::from);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<PaymentDTO> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.getPaymentsByCustomer(
                getCustomerId(), PageRequest.of(page, Math.min(size, 100))
        ).map(PaymentDTO::from);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<InvoiceBillDTO> getInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;
        return invoiceBillService.getInvoicesByCustomer(
                getCustomerId(), null, paymentStatus, from, to,
                PageRequest.of(page, Math.min(size, 100))
        ).map(InvoiceBillDTO::from);
    }

    @GetMapping("/vehicles")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<VehicleDTO> getVehicles() {
        return vehicleService.getVehiclesByCustomerId(getCustomerId())
                .stream().map(VehicleDTO::from).toList();
    }

    @GetMapping("/credit-info")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> getCreditInfo() {
        return customerService.getCreditInfo(getCustomerId());
    }
}
