package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CreditPolicy;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CreditPolicyRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditPolicyService {

    private final CreditPolicyRepository creditPolicyRepository;

    /**
     * Returns the effective policy for a customer:
     * 1. Category-specific policy if customer has a category and a policy exists for it
     * 2. Default (null-category) policy as fallback
     * 3. Sensible hardcoded defaults if no policy exists at all
     */
    @Transactional(readOnly = true)
    public CreditPolicy getEffectivePolicy(Customer customer) {
        Long scid = SecurityUtils.getScid();

        // Try category-specific policy
        if (customer.getCustomerCategory() != null) {
            var categoryPolicy = creditPolicyRepository
                    .findByCustomerCategoryIdAndScid(customer.getCustomerCategory().getId(), scid);
            if (categoryPolicy.isPresent()) {
                return categoryPolicy.get();
            }
        }

        // Fallback to default policy
        return creditPolicyRepository.findDefaultByScid(scid)
                .orElseGet(this::buildHardcodedDefaults);
    }

    @Transactional(readOnly = true)
    public List<CreditPolicy> getAllPolicies() {
        return creditPolicyRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public CreditPolicy getPolicyById(Long id) {
        return creditPolicyRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Credit policy not found with id: " + id));
    }

    @Transactional
    public CreditPolicy createPolicy(CreditPolicy policy) {
        return creditPolicyRepository.save(policy);
    }

    @Transactional
    public CreditPolicy updatePolicy(Long id, CreditPolicy updates) {
        CreditPolicy existing = getPolicyById(id);
        existing.setPolicyName(updates.getPolicyName());
        existing.setCustomerCategory(updates.getCustomerCategory());
        existing.setAgingBlockDays(updates.getAgingBlockDays());
        existing.setAgingWatchDays(updates.getAgingWatchDays());
        existing.setUtilizationWarnPercent(updates.getUtilizationWarnPercent());
        existing.setUtilizationBlockPercent(updates.getUtilizationBlockPercent());
        existing.setAutoBlockEnabled(updates.getAutoBlockEnabled());
        return creditPolicyRepository.save(existing);
    }

    @Transactional
    public void deletePolicy(Long id) {
        CreditPolicy policy = getPolicyById(id);
        creditPolicyRepository.delete(policy);
    }

    private CreditPolicy buildHardcodedDefaults() {
        CreditPolicy defaults = new CreditPolicy();
        defaults.setPolicyName("System Defaults");
        defaults.setAgingBlockDays(90);
        defaults.setAgingWatchDays(60);
        defaults.setUtilizationWarnPercent(80);
        defaults.setUtilizationBlockPercent(100);
        defaults.setAutoBlockEnabled(true);
        return defaults;
    }
}
