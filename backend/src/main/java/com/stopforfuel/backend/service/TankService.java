package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TankService {

    @Autowired
    private TankRepository tankRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private NozzleRepository nozzleRepository;

    public List<Tank> getAllTanks() {
        return tankRepository.findAllByScid(SecurityUtils.getScid());
    }

    public List<Tank> getActiveTanks() {
        return tankRepository.findByActiveAndScid(true, SecurityUtils.getScid());
    }

    public List<Tank> getTanksByProduct(Long productId) {
        return tankRepository.findByProductIdAndScid(productId, SecurityUtils.getScid());
    }

    public Tank getTankById(Long id) {
        return tankRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Tank not found with id: " + id));
    }

    public Tank createTank(Tank tank) {
        // Ensure the product exists
        if (tank.getProduct() != null && tank.getProduct().getId() != null) {
            Product product = productRepository.findById(tank.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + tank.getProduct().getId()));
            tank.setProduct(product);
        }
        if (tank.getScid() == null) {
            tank.setScid(SecurityUtils.getScid());
        }
        if (tank.getAvailableStock() == null) {
            tank.setAvailableStock(0.0);
        }
        return tankRepository.save(tank);
    }

    public Tank updateTank(Long id, Tank tankDetails) {
        Tank tank = getTankById(id);
        tank.setName(tankDetails.getName());
        tank.setCapacity(tankDetails.getCapacity());
        if (tankDetails.getAvailableStock() != null) {
            tank.setAvailableStock(tankDetails.getAvailableStock());
        }
        tank.setThresholdStock(tankDetails.getThresholdStock());
        if (tankDetails.getProduct() != null && tankDetails.getProduct().getId() != null) {
            Product product = productRepository.findById(tankDetails.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + tankDetails.getProduct().getId()));
            tank.setProduct(product);
        }
        // Cascade: deactivate connected nozzles when tank is set inactive
        if (!tankDetails.isActive() && tank.isActive()) {
            List<Nozzle> connectedNozzles = nozzleRepository.findByTankIdAndScid(id, SecurityUtils.getScid());
            for (Nozzle nozzle : connectedNozzles) {
                if (nozzle.isActive()) {
                    nozzle.setActive(false);
                    nozzleRepository.save(nozzle);
                }
            }
        }
        tank.setActive(tankDetails.isActive());
        return tankRepository.save(tank);
    }

    public Tank toggleStatus(Long id) {
        Tank tank = getTankById(id);
        boolean newStatus = !tank.isActive();
        // Cascade: deactivate connected nozzles when tank is set inactive
        if (!newStatus) {
            List<Nozzle> connectedNozzles = nozzleRepository.findByTankIdAndScid(id, SecurityUtils.getScid());
            for (Nozzle nozzle : connectedNozzles) {
                if (nozzle.isActive()) {
                    nozzle.setActive(false);
                    nozzleRepository.save(nozzle);
                }
            }
        }
        tank.setActive(newStatus);
        return tankRepository.save(tank);
    }

    public List<Tank> getLowStockTanks() {
        return tankRepository.findByActiveAndScid(true, SecurityUtils.getScid()).stream()
                .filter(Tank::isBelowThreshold)
                .toList();
    }

    public void deleteTank(Long id) {
        Tank tank = getTankById(id);
        tankRepository.delete(tank);
    }
}
