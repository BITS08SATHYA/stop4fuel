package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TankService {

    @Autowired
    private TankRepository tankRepository;

    @Autowired
    private ProductRepository productRepository;

    public List<Tank> getAllTanks() {
        return tankRepository.findAll();
    }

    public List<Tank> getActiveTanks() {
        return tankRepository.findByActive(true);
    }

    public List<Tank> getTanksByProduct(Long productId) {
        return tankRepository.findByProductId(productId);
    }

    public Tank getTankById(Long id) {
        return tankRepository.findById(id)
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
            tank.setScid(1L);
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
        if (tankDetails.getProduct() != null && tankDetails.getProduct().getId() != null) {
            Product product = productRepository.findById(tankDetails.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + tankDetails.getProduct().getId()));
            tank.setProduct(product);
        }
        return tankRepository.save(tank);
    }

    public Tank toggleStatus(Long id) {
        Tank tank = getTankById(id);
        tank.setActive(!tank.isActive());
        return tankRepository.save(tank);
    }

    public void deleteTank(Long id) {
        tankRepository.deleteById(id);
    }
}
