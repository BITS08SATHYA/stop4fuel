package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.IncentivePaymentDTO;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.IncentivePayment;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.service.IncentivePaymentExcelService;
import com.stopforfuel.backend.service.IncentivePaymentPdfGenerator;
import com.stopforfuel.backend.service.IncentivePaymentService;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/incentive-payments")
@RequiredArgsConstructor
public class IncentivePaymentController {

    private final IncentivePaymentService service;
    private final IncentivePaymentExcelService excelService;
    private final IncentivePaymentPdfGenerator pdfGenerator;
    private final CompanyRepository companyRepository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> getAll() {
        return service.getAll().stream().map(IncentivePaymentDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public IncentivePaymentDTO getById(@PathVariable Long id) {
        return IncentivePaymentDTO.from(service.getById(id));
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId).stream().map(IncentivePaymentDTO::from).toList();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> getByCustomer(@PathVariable Long customerId) {
        return service.getByCustomer(customerId).stream().map(IncentivePaymentDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}/total")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public BigDecimal getShiftTotal(@PathVariable Long shiftId) {
        return service.sumByShift(shiftId);
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.getByDateRange(fromDate, toDate).stream().map(IncentivePaymentDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public IncentivePaymentDTO create(@Valid @RequestBody IncentivePayment payment) {
        return IncentivePaymentDTO.from(service.create(payment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<IncentivePayment> payments = service.getByDateRange(fromDate, toDate);
        String companyName = companyRepository.findByScid(SecurityUtils.getScid()).stream()
                .findFirst().map(Company::getName).orElse("");
        byte[] bytes = excelService.generateExcel(payments, companyName, fromDate, toDate);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=incentive_payments_" + fromDate + "_" + toDate + ".xlsx");
        headers.add(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<IncentivePayment> payments = service.getByDateRange(fromDate, toDate);
        Company company = companyRepository.findByScid(SecurityUtils.getScid()).stream()
                .findFirst().orElse(null);
        byte[] bytes = pdfGenerator.generate(payments, company, fromDate, toDate);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=incentive_payments_" + fromDate + "_" + toDate + ".pdf");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/pdf");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
