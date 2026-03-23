package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Incentive;
import com.stopforfuel.backend.repository.IncentiveRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IncentiveService {

    private final IncentiveRepository repository;

    public List<Incentive> getAll() {
        return repository.findAllWithCustomerAndProduct(SecurityUtils.getScid());
    }

    public List<Incentive> getByCustomer(Long customerId) {
        return repository.findByCustomerId(customerId);
    }

    public Optional<Incentive> getActiveIncentive(Long customerId, Long productId) {
        return repository.findByCustomerIdAndProductIdAndActiveTrue(customerId, productId);
    }

    @Transactional
    public Incentive create(Incentive incentive) {
        if (incentive.getScid() == null) {
            incentive.setScid(SecurityUtils.getScid());
        }
        return repository.save(incentive);
    }

    @Transactional
    public Incentive update(Long id, Incentive details) {
        Incentive incentive = repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Incentive not found with id: " + id));
        incentive.setMinQuantity(details.getMinQuantity());
        incentive.setDiscountRate(details.getDiscountRate());
        incentive.setActive(details.isActive());
        return repository.save(incentive);
    }

    @Transactional
    public void deactivate(Long id) {
        Incentive incentive = repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Incentive not found with id: " + id));
        incentive.setActive(false);
        repository.save(incentive);
    }
}
