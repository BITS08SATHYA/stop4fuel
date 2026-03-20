package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.service.MappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mappings")
public class MappingController {

    @Autowired
    private MappingService mappingService;

    @GetMapping("/unassigned-customers")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<Customer> getUnassignedCustomers() {
        return mappingService.getUnassignedCustomers();
    }

    @GetMapping("/unassigned-vehicles")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<Vehicle> getUnassignedVehicles() {
        return mappingService.getUnassignedVehicles();
    }

    @PatchMapping("/assign-customers-to-group")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public List<Customer> assignCustomersToGroup(@RequestBody Map<String, Object> body) {
        List<Long> customerIds = ((List<Number>) body.get("customerIds")).stream()
                .map(Number::longValue).toList();
        Long groupId = ((Number) body.get("groupId")).longValue();
        return mappingService.assignCustomersToGroup(customerIds, groupId);
    }

    @PatchMapping("/unassign-customers-from-group")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public List<Customer> unassignCustomersFromGroup(@RequestBody Map<String, Object> body) {
        List<Long> customerIds = ((List<Number>) body.get("customerIds")).stream()
                .map(Number::longValue).toList();
        return mappingService.unassignCustomersFromGroup(customerIds);
    }

    @PatchMapping("/assign-vehicles-to-customer")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public List<Vehicle> assignVehiclesToCustomer(@RequestBody Map<String, Object> body) {
        List<Long> vehicleIds = ((List<Number>) body.get("vehicleIds")).stream()
                .map(Number::longValue).toList();
        Long customerId = ((Number) body.get("customerId")).longValue();
        return mappingService.assignVehiclesToCustomer(vehicleIds, customerId);
    }

    @PatchMapping("/unassign-vehicles-from-customer")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public List<Vehicle> unassignVehiclesFromCustomer(@RequestBody Map<String, Object> body) {
        List<Long> vehicleIds = ((List<Number>) body.get("vehicleIds")).stream()
                .map(Number::longValue).toList();
        return mappingService.unassignVehiclesFromCustomer(vehicleIds);
    }
}
