package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ShiftClosingDataDTO;
import com.stopforfuel.backend.dto.ShiftClosingSubmitDTO;
import com.stopforfuel.backend.dto.ShiftDTO;
import com.stopforfuel.backend.dto.UserListDTO;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.ShiftRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.backend.service.ShiftCashInvoiceAutoService;
import com.stopforfuel.backend.service.ShiftService;
import com.stopforfuel.config.SecurityUtils;
import com.stopforfuel.backend.service.ShiftTestDataSeeder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService service;
    private final ShiftTestDataSeeder testDataSeeder;
    private final UserRepository userRepository;
    private final ShiftCashInvoiceAutoService shiftCashInvoiceAutoService;
    private final ShiftRepository shiftRepository;

    public ShiftController(ShiftService service,
                           @Autowired(required = false) ShiftTestDataSeeder testDataSeeder,
                           UserRepository userRepository,
                           ShiftCashInvoiceAutoService shiftCashInvoiceAutoService,
                           ShiftRepository shiftRepository) {
        this.service = service;
        this.testDataSeeder = testDataSeeder;
        this.userRepository = userRepository;
        this.shiftCashInvoiceAutoService = shiftCashInvoiceAutoService;
        this.shiftRepository = shiftRepository;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ShiftDTO> getAll() {
        return service.getAllShifts().stream().map(ShiftDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ShiftDTO getActive() {
        Shift active = service.getActiveShift();
        return active != null ? ShiftDTO.from(active) : null;
    }

    @GetMapping("/postable")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ShiftDTO> getPostable(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return service.getPostableShifts(limit).stream().map(ShiftDTO::from).toList();
    }

    @GetMapping("/cashiers")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<UserListDTO> getCashiers() {
        Long scid = SecurityUtils.getScid();
        List<User> cashiers = userRepository.findByRoleRoleTypeAndScidAndStatus("CASHIER", scid, EntityStatus.ACTIVE);
        List<User> admins = userRepository.findByRoleRoleTypeAndScidAndStatus("ADMIN", scid, EntityStatus.ACTIVE);
        List<User> all = new java.util.ArrayList<>(cashiers);
        all.addAll(admins);
        return all.stream().map(UserListDTO::from).toList();
    }

    @PatchMapping("/{id}/attendant")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO changeAttendant(@PathVariable Long id, @RequestBody java.util.Map<String, Long> body) {
        Long attendantId = body.get("attendantId");
        if (attendantId == null) throw new IllegalArgumentException("attendantId is required");
        return ShiftDTO.from(service.changeAttendant(id, attendantId));
    }

    @PostMapping("/open")
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public ShiftDTO open(@Valid @RequestBody Shift shift) {
        return ShiftDTO.from(service.openShift(shift));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO close(@PathVariable Long id) {
        return ShiftDTO.from(service.closeShift(id));
    }

    // ========== Shift Closing Workspace ==========

    @GetMapping("/{id}/closing-data")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ShiftClosingDataDTO getClosingData(@PathVariable Long id) {
        return service.getShiftClosingData(id);
    }

    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO submitForReview(@PathVariable Long id, @Valid @RequestBody ShiftClosingSubmitDTO dto) {
        return ShiftDTO.from(service.submitForReview(id, dto));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO approve(@PathVariable Long id) {
        return ShiftDTO.from(service.approveAndClose(id));
    }

    @PostMapping("/{id}/generate-cash-bills")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO generateCashBills(@PathVariable Long id) {
        shiftCashInvoiceAutoService.generateForShift(id);
        return ShiftDTO.from(shiftRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found: " + id)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO reopen(@PathVariable Long id) {
        return ShiftDTO.from(service.reopenForReview(id));
    }

    @PostMapping("/{id}/reopen-to-edit")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO reopenToEdit(@PathVariable Long id) {
        return ShiftDTO.from(service.reopenToEdit(id));
    }

    @PostMapping("/{id}/seed-test-data")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public Map<String, Object> seedTestData(@PathVariable Long id) {
        if (testDataSeeder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test data seeding is not available in this environment");
        }
        return testDataSeeder.seedTestData(id);
    }
}
