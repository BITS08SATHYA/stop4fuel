package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.DuplicateResourceException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<Vehicle> getAllVehicles(String search) {
        if (search != null && !search.isEmpty()) {
            return vehicleRepository.findByVehicleNumberContainingIgnoreCaseWithCustomer(search);
        }
        return vehicleRepository.findAllWithCustomer();
    }

    @Transactional(readOnly = true)
    public Page<Vehicle> searchPaged(String search, EntityStatus status, Long customerId, Pageable pageable) {
        String s = (search != null) ? search.trim() : "";
        return vehicleRepository.searchPaged(s, status, customerId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> searchVehicles(String query) {
        return searchVehicles(query, null);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> searchVehicles(String query, String typeName) {
        String s = query == null ? "" : query.trim();
        if (s.isEmpty()) {
            return List.of();
        }
        // Empty-string sentinel for typeName matches the JPQL `:typeName = ''` no-filter clause.
        // Cannot bind null inside LOWER() — Postgres resolves lower(?) → lower(bytea) and throws
        // SQLState 42883. Same fix as f7bfa86 for the :search param on searchPaged.
        String type = (typeName == null) ? "" : typeName.trim();
        return vehicleRepository.findForSuggestion(s, type);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getVehiclesByCustomerId(Long customerId) {
        return vehicleRepository.findByCustomerIdWithCustomer(customerId);
    }

    @Transactional
    public Vehicle createVehicle(Vehicle vehicle) {
        // Check for duplicate vehicle number
        Optional<Vehicle> existing = vehicleRepository.findByVehicleNumber(vehicle.getVehicleNumber());
        if (existing.isPresent()) {
            throw new DuplicateResourceException("Vehicle with number '" + vehicle.getVehicleNumber() + "' already exists");
        }

        if (vehicle.getStatus() == null) {
            vehicle.setStatus(EntityStatus.ACTIVE);
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
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        // Check for duplicate vehicle number (exclude current vehicle)
        Optional<Vehicle> existing = vehicleRepository.findByVehicleNumber(vehicleDetails.getVehicleNumber());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new DuplicateResourceException("Vehicle with number '" + vehicleDetails.getVehicleNumber() + "' already exists");
        }

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
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        EntityStatus current = vehicle.getStatus();
        if (current == null || current == EntityStatus.ACTIVE) {
            vehicle.setStatus(EntityStatus.INACTIVE);
        } else {
            // Both INACTIVE and BLOCKED can be manually set back to ACTIVE
            vehicle.setStatus(EntityStatus.ACTIVE);
        }
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public Vehicle blockVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        if (vehicle.getStatus() != EntityStatus.ACTIVE) {
            throw new BusinessException("Vehicle can only be blocked from ACTIVE status. Current: " + vehicle.getStatus());
        }
        vehicle.setStatus(EntityStatus.BLOCKED);
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public Vehicle unblockVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        if (vehicle.getStatus() != EntityStatus.BLOCKED) {
            throw new BusinessException("Vehicle can only be unblocked from BLOCKED status. Current: " + vehicle.getStatus());
        }
        vehicle.setStatus(EntityStatus.ACTIVE);
        return vehicleRepository.save(vehicle);
    }

    /**
     * Updates only the maxLitersPerMonth for a vehicle.
     * Used by the "Set as Limit" workflow from statements.
     */
    @Transactional
    public Vehicle updateLiterLimit(Long id, Object maxLitersPerMonth) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        if (maxLitersPerMonth != null) {
            vehicle.setMaxLitersPerMonth(new BigDecimal(maxLitersPerMonth.toString()));
        } else {
            vehicle.setMaxLitersPerMonth(null);
        }
        validateVehicleLiterLimit(vehicle, id);
        return vehicleRepository.save(vehicle);
    }

    /**
     * Vehicle list for the inline VEHICLE_WISE order editor on /customers/statement-order.
     * Returns lightweight maps (id + vehicleNumber + statementOrder) ordered for predictable
     * rendering. Excludes INACTIVE vehicles.
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getVehicleStatementOrderList(Long customerId) {
        return vehicleRepository.findByCustomerIdOrderByStatementOrder(customerId).stream()
                .filter(v -> v.getStatus() != EntityStatus.INACTIVE)
                .map(v -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("vehicleNumber", v.getVehicleNumber());
                    m.put("statementOrder", v.getStatementOrder());
                    m.put("status", v.getStatus() != null ? v.getStatus().name() : null);
                    return m;
                })
                .toList();
    }

    /**
     * Bulk-update vehicle statement_order. Validates no duplicate non-negative orders within a
     * single customer's vehicle set (post-state). Negative values are skip sentinels and may
     * repeat. On conflict throws BusinessException with details and persists nothing.
     * Returns the customer's updated vehicle list (same shape as getVehicleStatementOrderList).
     */
    @Transactional
    public List<java.util.Map<String, Object>> bulkUpdateVehicleStatementOrder(java.util.Map<Long, Integer> updates) {
        if (updates == null || updates.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Vehicle> vehicles = vehicleRepository.findByIdIn(new java.util.ArrayList<>(updates.keySet()));
        java.util.Map<Long, Vehicle> byId = new java.util.HashMap<>();
        for (Vehicle v : vehicles) byId.put(v.getId(), v);

        List<Long> unknown = updates.keySet().stream().filter(id -> !byId.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException("Unknown vehicle ids: " + unknown);
        }

        // Group payload vehicles by customer, then check for duplicate non-negative orders
        // in the post-state for each customer (other vehicles for the same customer that
        // weren't in the payload retain their existing orders).
        java.util.Map<Long, List<Vehicle>> byCustomer = new java.util.LinkedHashMap<>();
        for (Vehicle v : vehicles) {
            if (v.getCustomer() == null) continue;
            byCustomer.computeIfAbsent(v.getCustomer().getId(), k -> new java.util.ArrayList<>()).add(v);
        }

        for (var entry : byCustomer.entrySet()) {
            Long custId = entry.getKey();
            List<Vehicle> customerVehiclesAll = vehicleRepository.findByCustomerId(custId);
            java.util.Map<Long, Integer> postState = new java.util.HashMap<>();
            for (Vehicle v : customerVehiclesAll) postState.put(v.getId(), v.getStatementOrder());
            for (var u : updates.entrySet()) {
                if (postState.containsKey(u.getKey())) postState.put(u.getKey(), u.getValue());
            }
            java.util.Map<Integer, List<Long>> byOrder = new java.util.LinkedHashMap<>();
            for (var ps : postState.entrySet()) {
                Integer ord = ps.getValue();
                if (ord == null || ord < 0) continue;
                byOrder.computeIfAbsent(ord, k -> new java.util.ArrayList<>()).add(ps.getKey());
            }
            for (var conflict : byOrder.entrySet()) {
                if (conflict.getValue().size() > 1) {
                    throw new BusinessException(
                            "Duplicate vehicle order " + conflict.getKey() + " within customer "
                                    + custId + " (vehicles " + conflict.getValue() + ")");
                }
            }
        }

        // Apply
        for (var u : updates.entrySet()) {
            Vehicle v = byId.get(u.getKey());
            v.setStatementOrder(u.getValue());
            vehicleRepository.save(v);
        }

        // Return all affected customers' vehicle lists (caller can pick the one it asked about)
        java.util.Map<Long, java.util.Map<String, Object>> seen = new java.util.LinkedHashMap<>();
        for (Long custId : byCustomer.keySet()) {
            for (var row : getVehicleStatementOrderList(custId)) {
                seen.put((Long) row.get("id"), row);
            }
        }
        return new java.util.ArrayList<>(seen.values());
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
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

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
            throw new BusinessException(
                    "Vehicle liter limit exceeds customer's credit limit. " +
                    "Customer limit: " + customer.getCreditLimitLiters() + " L, " +
                    "Already allocated: " + existingSum + " L, " +
                    "Remaining: " + remaining + " L, " +
                    "Requested: " + vehicle.getMaxLitersPerMonth() + " L"
            );
        }
    }
}
