package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.CustomerDetailDTO;
import com.stopforfuel.backend.dto.CustomerListDTO;
import com.stopforfuel.backend.dto.CustomerMapDTO;
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
    public org.springframework.data.domain.Page<CustomerListDTO> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryType) {
        return customerService.getCustomers(search, groupId, status, categoryType, org.springframework.data.domain.PageRequest.of(page, size))
                .map(CustomerListDTO::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public CustomerDetailDTO getCustomerById(@PathVariable Long id) {
        return CustomerDetailDTO.from(customerService.getCustomerById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerDetailDTO createCustomer(@Valid @RequestBody Customer customer) {
        if (customer.getScid() == null) {
            customer.setScid(SecurityUtils.getScid());
        }
        return CustomerDetailDTO.from(customerService.createCustomer(customer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerDetailDTO updateCustomer(@PathVariable Long id, @Valid @RequestBody Customer customer) {
        return CustomerDetailDTO.from(customerService.updateCustomer(id, customer));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public void deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
    }

    @GetMapping("/map-locations")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<CustomerMapDTO> getMapLocations() {
        return customerService.getCustomersWithCoordinates().stream()
                .map(CustomerMapDTO::from)
                .toList();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public java.util.Map<String, Object> getStats() {
        return customerService.getStats();
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerDetailDTO toggleStatus(@PathVariable Long id) {
        return CustomerDetailDTO.from(customerService.toggleStatus(id));
    }

    @PatchMapping("/{id}/block")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerDetailDTO blockCustomer(@PathVariable Long id) {
        return CustomerDetailDTO.from(customerService.blockCustomer(id));
    }

    @PatchMapping("/{id}/unblock")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerDetailDTO unblockCustomer(@PathVariable Long id) {
        return CustomerDetailDTO.from(customerService.unblockCustomer(id));
    }

    @GetMapping("/{id}/credit-info")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public java.util.Map<String, Object> getCreditInfo(@PathVariable Long id) {
        return customerService.getCreditInfo(id);
    }

    @PatchMapping("/{id}/credit-limits")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerDetailDTO updateCreditLimits(@PathVariable Long id, @RequestBody java.util.Map<String, Object> limits) {
        return CustomerDetailDTO.from(customerService.updateCreditLimits(id, limits));
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
