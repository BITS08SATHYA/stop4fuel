package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.StockAlert;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.repository.StockAlertRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockAlertService {

    private final StockAlertRepository stockAlertRepository;

    private final TankRepository tankRepository;

    @Transactional(readOnly = true)
    public List<StockAlert> getActiveAlerts() {
        return stockAlertRepository.findByActiveAndScid(true, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<StockAlert> getAllAlerts() {
        return stockAlertRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional
    public StockAlert acknowledgeAlert(Long alertId, String acknowledgedBy) {
        StockAlert alert = stockAlertRepository.findByIdAndScid(alertId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + alertId));
        alert.setActive(false);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(acknowledgedBy);
        return stockAlertRepository.save(alert);
    }

    @Transactional
    public void acknowledgeAllAlerts(String acknowledgedBy) {
        List<StockAlert> activeAlerts = stockAlertRepository.findByActiveAndScid(true, SecurityUtils.getScid());
        LocalDateTime now = LocalDateTime.now();
        for (StockAlert alert : activeAlerts) {
            alert.setActive(false);
            alert.setAcknowledgedAt(now);
            alert.setAcknowledgedBy(acknowledgedBy);
            stockAlertRepository.save(alert);
        }
    }

    /**
     * Check all active tanks and generate alerts for those below threshold.
     * Auto-resolves alerts for tanks that are no longer below threshold.
     */
    @Transactional
    public List<StockAlert> checkAndGenerateAlerts() {
        Long scid = SecurityUtils.getScid();
        List<Tank> activeTanks = tankRepository.findByActiveAndScid(true, scid);

        for (Tank tank : activeTanks) {
            if (tank.isBelowThreshold()) {
                // Check if there's already an active alert for this tank
                var existingAlert = stockAlertRepository.findFirstByTankIdAndActiveAndScid(tank.getId(), true, scid);
                if (existingAlert.isEmpty()) {
                    StockAlert alert = new StockAlert();
                    alert.setScid(scid);
                    alert.setTank(tank);
                    alert.setAvailableStock(tank.getAvailableStock());
                    alert.setThresholdStock(tank.getThresholdStock());
                    alert.setMessage(String.format(
                            "Low stock alert: %s has %.1f L remaining (threshold: %.1f L)",
                            tank.getName(), tank.getAvailableStock(), tank.getThresholdStock()
                    ));
                    alert.setActive(true);
                    stockAlertRepository.save(alert);
                }
            } else {
                // Auto-resolve active alerts for tanks that are back above threshold
                List<StockAlert> activeAlerts = stockAlertRepository.findByTankIdAndActiveAndScid(tank.getId(), true, scid);
                for (StockAlert alert : activeAlerts) {
                    alert.setActive(false);
                    alert.setAcknowledgedAt(LocalDateTime.now());
                    alert.setAcknowledgedBy("SYSTEM_AUTO_RESOLVED");
                    stockAlertRepository.save(alert);
                }
            }
        }

        return stockAlertRepository.findByActiveAndScid(true, scid);
    }
}
