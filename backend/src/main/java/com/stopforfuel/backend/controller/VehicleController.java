package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.VehicleDTO;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    @Autowired
    private VehicleService vehicleService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public Page<VehicleDTO> getAllVehicles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 25, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return vehicleService.searchPaged(search, status, customerId, pageable).map(VehicleDTO::from);
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<VehicleDTO> searchVehicles(@RequestParam String q,
                                           @RequestParam(required = false) String typeName) {
        return vehicleService.searchVehicles(q, typeName).stream().map(VehicleDTO::from).toList();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<VehicleDTO> getVehiclesByCustomerId(@PathVariable Long customerId) {
        return vehicleService.getVehiclesByCustomerId(customerId).stream().map(VehicleDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'VEHICLE_CREATE')")
    public VehicleDTO createVehicle(@Valid @RequestBody Vehicle vehicle) {
        return VehicleDTO.from(vehicleService.createVehicle(vehicle));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO updateVehicle(@PathVariable Long id, @Valid @RequestBody Vehicle vehicle) {
        return VehicleDTO.from(vehicleService.updateVehicle(id, vehicle));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_DELETE')")
    public void deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO toggleStatus(@PathVariable Long id) {
        return VehicleDTO.from(vehicleService.toggleStatus(id));
    }

    @PatchMapping("/{id}/block")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO blockVehicle(@PathVariable Long id) {
        return VehicleDTO.from(vehicleService.blockVehicle(id));
    }

    @PatchMapping("/{id}/unblock")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO unblockVehicle(@PathVariable Long id) {
        return VehicleDTO.from(vehicleService.unblockVehicle(id));
    }

    @PatchMapping("/{id}/liter-limit")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO updateLiterLimit(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        return VehicleDTO.from(vehicleService.updateLiterLimit(id, body.get("maxLitersPerMonth")));
    }

    /**
     * Lightweight per-customer vehicle list for the inline VEHICLE_WISE order editor on
     * /customers/statement-order. Returns only what the table needs (id, vehicleNumber,
     * statementOrder), ordered by statementOrder ASC nulls last for predictable rendering.
     */
    @GetMapping("/customer/{customerId}/statement-order")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public java.util.List<java.util.Map<String, Object>> getVehicleStatementOrderList(@PathVariable Long customerId) {
        return vehicleService.getVehicleStatementOrderList(customerId);
    }

    /**
     * Bulk-update statement_order on multiple vehicles. Body shape:
     * {@code { "updates": [ { "vehicleId": 1, "statementOrder": 3 }, ... ] } }.
     * Validates no duplicate non-negative orders WITHIN A SINGLE CUSTOMER (vehicles across
     * customers don't conflict — they're separate auto-gen iterations). Negative values
     * are skip sentinels and may repeat. Persists nothing on conflict.
     */
    @PatchMapping("/bulk/statement-order")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public java.util.List<java.util.Map<String, Object>> bulkUpdateVehicleStatementOrder(
            @RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> updates =
                (java.util.List<java.util.Map<String, Object>>) body.get("updates");
        java.util.Map<Long, Integer> map = new java.util.LinkedHashMap<>();
        if (updates != null) {
            for (var entry : updates) {
                Object idRaw = entry.get("vehicleId");
                Object orderRaw = entry.get("statementOrder");
                if (idRaw == null) continue;
                Long id = Long.valueOf(idRaw.toString());
                Integer order = orderRaw == null ? null : Integer.valueOf(orderRaw.toString());
                map.put(id, order);
            }
        }
        return vehicleService.bulkUpdateVehicleStatementOrder(map);
    }
}
