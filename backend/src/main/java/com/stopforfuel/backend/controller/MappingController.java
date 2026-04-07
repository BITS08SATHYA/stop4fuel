package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.CustomerListDTO;
import com.stopforfuel.backend.dto.VehicleDTO;
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
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public List<Customer> assignCustomersToGroup(@RequestBody Map<String, Object> body) {
        List<Long> customerIds = extractIdList(body, "customerIds");
        Long groupId = extractLong(body, "groupId");
        return mappingService.assignCustomersToGroup(customerIds, groupId);
    }

    @PatchMapping("/unassign-customers-from-group")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public List<Customer> unassignCustomersFromGroup(@RequestBody Map<String, Object> body) {
        List<Long> customerIds = extractIdList(body, "customerIds");
        return mappingService.unassignCustomersFromGroup(customerIds);
    }

    @PatchMapping("/assign-vehicles-to-customer")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public List<Vehicle> assignVehiclesToCustomer(@RequestBody Map<String, Object> body) {
        List<Long> vehicleIds = extractIdList(body, "vehicleIds");
        Long customerId = extractLong(body, "customerId");
        return mappingService.assignVehiclesToCustomer(vehicleIds, customerId);
    }

    @PatchMapping("/unassign-vehicles-from-customer")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public List<Vehicle> unassignVehiclesFromCustomer(@RequestBody Map<String, Object> body) {
        List<Long> vehicleIds = extractIdList(body, "vehicleIds");
        return mappingService.unassignVehiclesFromCustomer(vehicleIds);
    }

    @SuppressWarnings("unchecked")
    private List<Long> extractIdList(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("'" + key + "' is required and must be a non-empty list");
        }
        return ((List<Number>) list).stream().map(Number::longValue).toList();
    }

    private Long extractLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Number num)) {
            throw new IllegalArgumentException("'" + key + "' is required and must be a number");
        }
        return num.longValue();
    }
}
