package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.service.CustomerService;
import com.stopforfuel.backend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private VehicleService vehicleService;

    @GetMapping
    public org.springframework.data.domain.Page<Customer> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long groupId) {
        return customerService.getCustomers(search, groupId, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Customer getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    @PostMapping
    public Customer createCustomer(@Valid @RequestBody Customer customer) {
        // TODO: Get scid from SecurityContext
        if (customer.getScid() == null) {
            customer.setScid(1L);
        }
        return customerService.createCustomer(customer);
    }

    @PutMapping("/{id}")
    public Customer updateCustomer(@PathVariable Long id, @Valid @RequestBody Customer customer) {
        return customerService.updateCustomer(id, customer);
    }

    @DeleteMapping("/{id}")
    public void deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
    }

    @GetMapping("/stats")
    public java.util.Map<String, Object> getStats() {
        return customerService.getStats();
    }

    @PatchMapping("/{id}/toggle-status")
    public Customer toggleStatus(@PathVariable Long id) {
        return customerService.toggleStatus(id);
    }

    @PatchMapping("/{id}/block")
    public Customer blockCustomer(@PathVariable Long id) {
        return customerService.blockCustomer(id);
    }

    @PatchMapping("/{id}/unblock")
    public Customer unblockCustomer(@PathVariable Long id) {
        return customerService.unblockCustomer(id);
    }

    @GetMapping("/{id}/vehicles")
    public List<Vehicle> getVehiclesByCustomerId(@PathVariable Long id) {
        return vehicleService.getVehiclesByCustomerId(id);
    }
}
