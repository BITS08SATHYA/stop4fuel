package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.PumpSessionDTO;
import com.stopforfuel.backend.entity.PumpSession;
import com.stopforfuel.backend.service.PumpSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pump-sessions")
@RequiredArgsConstructor
public class PumpSessionController {

    private final PumpSessionService service;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public PumpSessionDTO startSession(@RequestBody Map<String, Object> request) {
        Long pumpId = Long.valueOf(request.get("pumpId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> openReadings = (List<Map<String, Object>>) request.get("readings");
        return PumpSessionDTO.from(service.startSession(pumpId, openReadings));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public PumpSessionDTO closeSession(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> closeReadings = (List<Map<String, Object>>) request.get("readings");
        return PumpSessionDTO.from(service.closeSession(id, closeReadings));
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public PumpSessionDTO getActive() {
        PumpSession active = service.getActiveSession();
        return active != null ? PumpSessionDTO.from(active) : null;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public PumpSessionDTO getById(@PathVariable Long id) {
        return PumpSessionDTO.from(service.getSession(id));
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<PumpSessionDTO> getByShift(@PathVariable Long shiftId) {
        return service.getSessionsByShift(shiftId).stream().map(PumpSessionDTO::from).toList();
    }
}
