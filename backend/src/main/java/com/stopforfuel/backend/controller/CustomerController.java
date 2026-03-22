package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.service.CustomerService;
import com.stopforfuel.backend.service.JasperReportService;
import com.stopforfuel.backend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.stopforfuel.config.SecurityUtils;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private JasperReportService jasperReportService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public org.springframework.data.domain.Page<Customer> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerCategory) {
        return customerService.getCustomers(search, groupId, status, customerCategory, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public Customer getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public Customer createCustomer(@Valid @RequestBody Customer customer) {
        if (customer.getScid() == null) {
            customer.setScid(SecurityUtils.getScid());
        }
        return customerService.createCustomer(customer);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public Customer updateCustomer(@PathVariable Long id, @Valid @RequestBody Customer customer) {
        return customerService.updateCustomer(id, customer);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public void deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public java.util.Map<String, Object> getStats() {
        return customerService.getStats();
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public Customer toggleStatus(@PathVariable Long id) {
        return customerService.toggleStatus(id);
    }

    @PatchMapping("/{id}/block")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public Customer blockCustomer(@PathVariable Long id) {
        return customerService.blockCustomer(id);
    }

    @PatchMapping("/{id}/unblock")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public Customer unblockCustomer(@PathVariable Long id) {
        return customerService.unblockCustomer(id);
    }

    @GetMapping("/{id}/vehicles")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<Vehicle> getVehiclesByCustomerId(@PathVariable Long id) {
        return vehicleService.getVehiclesByCustomerId(id);
    }

    @GetMapping("/{id}/vehicle-report/pdf")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public ResponseEntity<byte[]> downloadVehicleReport(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id);
        List<Vehicle> vehicles = vehicleService.getVehiclesByCustomerId(id);
        byte[] pdf = jasperReportService.generateCustomerVehicleReport(customer, vehicles);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"vehicle-report-" + customer.getName() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

}
