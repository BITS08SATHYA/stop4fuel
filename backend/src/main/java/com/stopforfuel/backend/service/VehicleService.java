package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class VehicleService {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    public List<Vehicle> getAllVehicles(String search) {
        if (search != null && !search.isEmpty()) {
            return vehicleRepository.findByVehicleNumberContainingIgnoreCase(search);
        }
        return vehicleRepository.findAll();
    }

    public List<Vehicle> searchVehicles(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return vehicleRepository.findByVehicleNumberContainingIgnoreCase(query.trim());
    }

    public List<Vehicle> getVehiclesByCustomerId(Long customerId) {
        return vehicleRepository.findByCustomerId(customerId);
    }

    @Transactional
    public Vehicle createVehicle(Vehicle vehicle) {
        if (vehicle.getStatus() == null) {
            vehicle.setStatus("ACTIVE");
        }
        if (vehicle.getConsumedLiters() == null) {
            vehicle.setConsumedLiters(BigDecimal.ZERO);
        }
        validateVehicleLiterLimit(vehicle, null);
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public Vehicle updateVehicle(Long id, Vehicle vehicleDetails) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));

        vehicle.setVehicleNumber(vehicleDetails.getVehicleNumber());
        vehicle.setMaxCapacity(vehicleDetails.getMaxCapacity());
        vehicle.setVehicleType(vehicleDetails.getVehicleType());
        vehicle.setPreferredProduct(vehicleDetails.getPreferredProduct());
        vehicle.setCustomer(vehicleDetails.getCustomer());

        if (vehicleDetails.getMaxLitersPerMonth() != null) {
            validateVehicleLiterLimit(vehicleDetails, id);
            vehicle.setMaxLitersPerMonth(vehicleDetails.getMaxLitersPerMonth());
        }

        return vehicleRepository.save(vehicle);
    }

    public void deleteVehicle(Long id) {
        vehicleRepository.deleteById(id);
    }

    @Transactional
    public Vehicle toggleStatus(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));

        String current = vehicle.getStatus();
        if (current == null || "ACTIVE".equals(current)) {
            vehicle.setStatus("INACTIVE");
        } else {
            // Both INACTIVE and BLOCKED can be manually set back to ACTIVE
            vehicle.setStatus("ACTIVE");
        }
        return vehicleRepository.save(vehicle);
    }

    /**
     * Validates that the sum of all vehicle liter limits for a customer
     * does not exceed the customer's creditLimitLiters.
     */
    private void validateVehicleLiterLimit(Vehicle vehicle, Long excludeVehicleId) {
        if (vehicle.getMaxLitersPerMonth() == null || vehicle.getCustomer() == null) {
            return;
        }

        Long customerId = vehicle.getCustomer().getId();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        if (customer.getCreditLimitLiters() == null) {
            return; // No limit set at customer level, skip validation
        }

        // Sum existing vehicle limits for this customer, excluding the current vehicle if updating
        BigDecimal existingSum = vehicleRepository.findByCustomerId(customerId).stream()
                .filter(v -> !v.getId().equals(excludeVehicleId))
                .map(v -> v.getMaxLitersPerMonth() != null ? v.getMaxLitersPerMonth() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = existingSum.add(vehicle.getMaxLitersPerMonth());

        if (newTotal.compareTo(customer.getCreditLimitLiters()) > 0) {
            BigDecimal remaining = customer.getCreditLimitLiters().subtract(existingSum);
            throw new RuntimeException(
                    "Vehicle liter limit exceeds customer's credit limit. " +
                    "Customer limit: " + customer.getCreditLimitLiters() + " L, " +
                    "Already allocated: " + existingSum + " L, " +
                    "Remaining: " + remaining + " L, " +
                    "Requested: " + vehicle.getMaxLitersPerMonth() + " L"
            );
        }
    }
}
