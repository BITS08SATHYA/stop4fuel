package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.PumpSessionStatus;
import com.stopforfuel.backend.enums.ShiftStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.backend.repository.PumpSessionRepository;
import com.stopforfuel.backend.repository.PumpRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PumpSessionService {

    private final PumpSessionRepository repository;
    private final PumpRepository pumpRepository;
    private final NozzleRepository nozzleRepository;
    private final UserRepository userRepository;
    private final ShiftService shiftService;

    @Transactional
    public PumpSession startSession(Long pumpId, List<Map<String, Object>> openReadings) {
        Long scid = SecurityUtils.getScid();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate active shift exists
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            throw new BusinessException("No active shift. Cannot start a pump session.");
        }

        // Check attendant doesn't already have an open session
        repository.findByAttendantIdAndStatusAndScid(userId, PumpSessionStatus.OPEN, scid)
                .ifPresent(s -> {
                    throw new BusinessException("You already have an open pump session on " + s.getPump().getName() + ". Close it first.");
                });

        // Check pump doesn't already have an open session
        repository.findByPumpIdAndStatusAndScid(pumpId, PumpSessionStatus.OPEN, scid)
                .ifPresent(s -> {
                    throw new BusinessException(s.getPump().getName() + " already has an active session by " + s.getAttendant().getName());
                });

        Pump pump = pumpRepository.findById(pumpId)
                .orElseThrow(() -> new ResourceNotFoundException("Pump not found"));

        User attendant = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PumpSession session = new PumpSession();
        session.setScid(scid);
        session.setShiftId(activeShift.getId());
        session.setPump(pump);
        session.setAttendant(attendant);
        session.setStartTime(LocalDateTime.now());
        session.setStatus(PumpSessionStatus.OPEN);

        // Create reading entries with open readings for each nozzle
        List<PumpSessionReading> readings = new ArrayList<>();
        for (Map<String, Object> input : openReadings) {
            Long nozzleId = Long.valueOf(input.get("nozzleId").toString());
            BigDecimal openReading = new BigDecimal(input.get("openReading").toString());

            Nozzle nozzle = nozzleRepository.findById(nozzleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Nozzle not found: " + nozzleId));

            // Validate nozzle belongs to the selected pump
            if (!nozzle.getPump().getId().equals(pumpId)) {
                throw new BusinessException("Nozzle " + nozzle.getNozzleName() + " does not belong to " + pump.getName());
            }

            PumpSessionReading reading = new PumpSessionReading();
            reading.setPumpSession(session);
            reading.setNozzle(nozzle);
            reading.setOpenReading(openReading);
            readings.add(reading);
        }
        session.setReadings(readings);

        return repository.save(session);
    }

    @Transactional
    public PumpSession closeSession(Long sessionId, List<Map<String, Object>> closeReadings) {
        Long scid = SecurityUtils.getScid();

        PumpSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pump session not found"));

        if (session.getStatus() != PumpSessionStatus.OPEN) {
            throw new BusinessException("Session is already closed");
        }

        session.setEndTime(LocalDateTime.now());
        session.setStatus(PumpSessionStatus.CLOSED);

        // Update close readings and compute totals
        for (Map<String, Object> input : closeReadings) {
            Long nozzleId = Long.valueOf(input.get("nozzleId").toString());
            BigDecimal closeReading = new BigDecimal(input.get("closeReading").toString());

            PumpSessionReading reading = session.getReadings().stream()
                    .filter(r -> r.getNozzle().getId().equals(nozzleId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("No opening reading found for nozzle " + nozzleId));

            reading.setCloseReading(closeReading);

            // Calculate liters sold = close - open
            BigDecimal liters = closeReading.subtract(reading.getOpenReading());
            reading.setLitersSold(liters);

            // Calculate sales amount = liters × product unit price
            BigDecimal unitPrice = BigDecimal.ZERO;
            if (reading.getNozzle().getTank() != null
                    && reading.getNozzle().getTank().getProduct() != null
                    && reading.getNozzle().getTank().getProduct().getPrice() != null) {
                unitPrice = reading.getNozzle().getTank().getProduct().getPrice();
            }
            reading.setSalesAmount(liters.multiply(unitPrice));
        }

        return repository.save(session);
    }

    @Transactional(readOnly = true)
    public PumpSession getActiveSession() {
        Long scid = SecurityUtils.getScid();
        Long userId = SecurityUtils.getCurrentUserId();
        return repository.findByAttendantIdAndStatusAndScid(userId, PumpSessionStatus.OPEN, scid)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PumpSession getSession(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pump session not found"));
    }

    @Transactional(readOnly = true)
    public List<PumpSession> getSessionsByShift(Long shiftId) {
        return repository.findByShiftIdAndScidOrderByStartTimeDesc(shiftId, SecurityUtils.getScid());
    }
}
