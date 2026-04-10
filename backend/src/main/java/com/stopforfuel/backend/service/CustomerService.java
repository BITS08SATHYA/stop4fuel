package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    private final VehicleRepository vehicleRepository;

    private final InvoiceBillRepository invoiceBillRepository;

    private final PaymentRepository paymentRepository;

    private final com.stopforfuel.backend.repository.RolesRepository rolesRepository;

    private final com.stopforfuel.backend.repository.CustomerBlockEventRepository blockEventRepository;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Customer> getCustomers(String search, Long groupId, String status, String categoryType, org.springframework.data.domain.Pageable pageable) {
        String cat = (categoryType != null && !categoryType.isEmpty()) ? categoryType : null;
        if (search != null && !search.isEmpty()) {
            return customerRepository.findBySearchAndFilters(search, groupId, status, cat, pageable);
        }
        return customerRepository.findByGroupAndStatus(groupId, status, cat, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Customer> getCustomersByGroupId(Long groupId, org.springframework.data.domain.Pageable pageable) {
        return customerRepository.findByGroupId(groupId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Customer> getCustomersWithCoordinates() {
        return customerRepository.findAllWithCoordinatesByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        return customerRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }

    public Customer createCustomer(Customer customer) {
        if (customer.getRole() == null) {
            com.stopforfuel.backend.entity.Roles customerRole = rolesRepository.findByRoleType("CUSTOMER")
                    .orElseThrow(() -> new ResourceNotFoundException("Error: Role is not found."));
            customer.setRole(customerRole);
        }
        if (customer.getStatus() == null) {
            customer.setStatus(EntityStatus.ACTIVE);
        }
        if (customer.getConsumedLiters() == null) {
            customer.setConsumedLiters(BigDecimal.ZERO);
        }
        return customerRepository.save(customer);
    }

    public Customer updateCustomer(Long id, Customer customerDetails) {
        Customer customer = getCustomerById(id);

        customer.setName(customerDetails.getName());
        customer.setUsername(customerDetails.getUsername());
        customer.setAddress(customerDetails.getAddress());
        customer.setEmails(customerDetails.getEmails());
        customer.setPhoneNumbers(customerDetails.getPhoneNumbers());
        customer.setCreditLimitAmount(customerDetails.getCreditLimitAmount());
        customer.setCreditLimitLiters(customerDetails.getCreditLimitLiters());
        customer.setGroup(customerDetails.getGroup());
        customer.setParty(customerDetails.getParty());
        customer.setJoinDate(customerDetails.getJoinDate());
        customer.setGstNumber(customerDetails.getGstNumber());
        customer.setCustomerCategory(customerDetails.getCustomerCategory());
        customer.setLatitude(customerDetails.getLatitude());
        customer.setLongitude(customerDetails.getLongitude());
        customer.setStatementFrequency(customerDetails.getStatementFrequency());
        customer.setStatementGrouping(customerDetails.getStatementGrouping());

        return customerRepository.save(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        List<Vehicle> vehicles = customer.getVehicles();
        for (Vehicle vehicle : vehicles) {
            vehicle.setCustomer(null);
        }
        vehicleRepository.saveAll(vehicles);
        customerRepository.delete(customer);
    }

    /**
     * Toggle customer status: ACTIVE ↔ INACTIVE.
     * Also toggleable from BLOCKED → ACTIVE (admin unblock).
     */
    @Transactional
    public Customer toggleStatus(Long id) {
        Customer customer = getCustomerById(id);
        EntityStatus current = customer.getStatus();
        if (current == null || current == EntityStatus.ACTIVE) {
            customer.setStatus(EntityStatus.INACTIVE);
        } else {
            // Both INACTIVE and BLOCKED can be manually set back to ACTIVE
            customer.setStatus(EntityStatus.ACTIVE);
        }
        return customerRepository.save(customer);
    }

    /**
     * Resets consumed liters for all customers AND vehicles on the 1st of every month.
     * Does NOT auto-unblock — admin must manually unblock.
     * Note: Intentionally uses findAll() (cross-tenant) since this is a system-wide scheduled job.
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    public void resetConsumedLiters() {
        List<Customer> customers = customerRepository.findAll();
        for (Customer customer : customers) {
            customer.setConsumedLiters(BigDecimal.ZERO);
        }
        customerRepository.saveAll(customers);

        List<Vehicle> vehicles = vehicleRepository.findAll();
        for (Vehicle vehicle : vehicles) {
            vehicle.setConsumedLiters(BigDecimal.ZERO);
        }
        vehicleRepository.saveAll(vehicles);
    }

    /**
     * Block a customer (only if currently ACTIVE).
     */
    @Transactional
    public Customer blockCustomer(Long id) {
        return blockCustomer(id, null);
    }

    /**
     * Block a customer with optional notes for the audit trail.
     */
    @Transactional
    public Customer blockCustomer(Long id, String notes) {
        Customer customer = getCustomerById(id);
        if (customer.getStatus() != EntityStatus.ACTIVE) {
            throw new BusinessException("Customer can only be blocked when ACTIVE. Current status: " + customer.getStatus());
        }
        String previousStatus = customer.getStatus() != null ? customer.getStatus().name() : "ACTIVE";
        customer.setStatus(EntityStatus.BLOCKED);
        customer.setLastBlockedAt(LocalDateTime.now());
        customer.setBlockCount(customer.getBlockCount() != null ? customer.getBlockCount() + 1 : 1);
        Customer saved = customerRepository.save(customer);

        // Record block event
        com.stopforfuel.backend.entity.CustomerBlockEvent event = new com.stopforfuel.backend.entity.CustomerBlockEvent();
        event.setCustomer(saved);
        event.setScid(saved.getScid());
        event.setEventType("BLOCKED");
        event.setTriggerType("MANUAL");
        event.setReason("Manual block by admin");
        event.setNotes(notes);
        event.setPreviousStatus(previousStatus);
        blockEventRepository.save(event);

        return saved;
    }

    /**
     * Unblock a customer (only if currently BLOCKED).
     */
    @Transactional
    public Customer unblockCustomer(Long id) {
        return unblockCustomer(id, null);
    }

    /**
     * Unblock a customer with optional notes for the audit trail.
     */
    @Transactional
    public Customer unblockCustomer(Long id, String notes) {
        Customer customer = getCustomerById(id);
        if (customer.getStatus() != EntityStatus.BLOCKED) {
            throw new BusinessException("Customer can only be unblocked when BLOCKED. Current status: " + customer.getStatus());
        }
        customer.setStatus(EntityStatus.ACTIVE);
        Customer saved = customerRepository.save(customer);

        // Record unblock event
        com.stopforfuel.backend.entity.CustomerBlockEvent event = new com.stopforfuel.backend.entity.CustomerBlockEvent();
        event.setCustomer(saved);
        event.setScid(saved.getScid());
        event.setEventType("UNBLOCKED");
        event.setTriggerType("MANUAL");
        event.setReason("Manual unblock by admin");
        event.setNotes(notes);
        event.setPreviousStatus("BLOCKED");
        blockEventRepository.save(event);

        return saved;
    }

    /**
     * Pre-invoice validation: checks if a new credit invoice would exceed the customer's limits.
     * Returns null if OK, or an error message string if limit would be breached.
     */
    @Transactional
    public Customer toggleForceUnblock(Long id, boolean enabled, String byUser) {
        Customer customer = getCustomerById(id);
        customer.setForceUnblocked(enabled);
        customer.setForceUnblockedAt(enabled ? LocalDateTime.now() : null);
        customer.setForceUnblockedBy(enabled ? byUser : null);
        Customer saved = customerRepository.save(customer);

        com.stopforfuel.backend.entity.CustomerBlockEvent event = new com.stopforfuel.backend.entity.CustomerBlockEvent();
        event.setCustomer(saved);
        event.setScid(saved.getScid());
        event.setEventType(enabled ? "FORCE_UNBLOCKED" : "FORCE_UNBLOCK_REMOVED");
        event.setTriggerType("FORCE_UNBLOCK");
        event.setReason(enabled ? "Force unblock enabled by " + byUser : "Force unblock removed by " + byUser);
        event.setPreviousStatus(saved.getStatus() != null ? saved.getStatus().name() : "ACTIVE");
        blockEventRepository.save(event);

        return saved;
    }

    public String validateCreditLimitBeforeInvoice(Long customerId, BigDecimal invoiceAmount, BigDecimal invoiceLiters) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) return null;
        if (customer.isForceUnblocked()) return null;

        // 1. Amount-based check: would new invoice push current period's unbilled credit beyond creditLimitAmount?
        if (customer.getCreditLimitAmount() != null && customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) > 0
                && invoiceAmount != null) {
            BigDecimal unbilledCredit = invoiceBillRepository.sumUnbilledCreditByCustomer(customerId);
            BigDecimal projectedUnbilled = unbilledCredit.add(invoiceAmount);
            if (projectedUnbilled.compareTo(customer.getCreditLimitAmount()) > 0) {
                BigDecimal remaining = customer.getCreditLimitAmount().subtract(unbilledCredit);
                return "Customer '" + customer.getName() + "' credit limit would be exceeded. "
                        + "Limit: ₹" + customer.getCreditLimitAmount().toPlainString()
                        + ", Unbilled credit: ₹" + unbilledCredit.toPlainString()
                        + ", This invoice: ₹" + invoiceAmount.toPlainString()
                        + ", Remaining: ₹" + (remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining.toPlainString() : "0");
            }
        }

        // 2. Liter-based check: would new invoice push consumed liters beyond creditLimitLiters?
        if (customer.getCreditLimitLiters() != null && customer.getCreditLimitLiters().compareTo(BigDecimal.ZERO) > 0
                && invoiceLiters != null && invoiceLiters.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentConsumed = customer.getConsumedLiters() != null ? customer.getConsumedLiters() : BigDecimal.ZERO;
            BigDecimal projectedConsumed = currentConsumed.add(invoiceLiters);
            if (projectedConsumed.compareTo(customer.getCreditLimitLiters()) > 0) {
                BigDecimal remaining = customer.getCreditLimitLiters().subtract(currentConsumed);
                return "Customer '" + customer.getName() + "' liter limit would be exceeded. "
                        + "Limit: " + customer.getCreditLimitLiters().toPlainString() + " L"
                        + ", Consumed: " + currentConsumed.toPlainString() + " L"
                        + ", This invoice: " + invoiceLiters.toPlainString() + " L"
                        + ", Remaining: " + (remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining.toPlainString() : "0") + " L";
            }
        }

        return null; // OK
    }

    /**
     * Pre-invoice validation: checks if a new invoice would exceed the vehicle's monthly liter limit.
     * Returns null if OK, or an error message string if limit would be breached.
     */
    public String validateVehicleLimitBeforeInvoice(Long vehicleId, BigDecimal invoiceLiters) {
        if (invoiceLiters == null || invoiceLiters.compareTo(BigDecimal.ZERO) <= 0) return null;

        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
        if (vehicle == null) return null;

        if (vehicle.getMaxLitersPerMonth() != null && vehicle.getMaxLitersPerMonth().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentConsumed = vehicle.getConsumedLiters() != null ? vehicle.getConsumedLiters() : BigDecimal.ZERO;
            BigDecimal projectedConsumed = currentConsumed.add(invoiceLiters);
            if (projectedConsumed.compareTo(vehicle.getMaxLitersPerMonth()) > 0) {
                BigDecimal remaining = vehicle.getMaxLitersPerMonth().subtract(currentConsumed);
                return "Vehicle '" + vehicle.getVehicleNumber() + "' monthly liter limit would be exceeded. "
                        + "Limit: " + vehicle.getMaxLitersPerMonth().toPlainString() + " L"
                        + ", Consumed: " + currentConsumed.toPlainString() + " L"
                        + ", This invoice: " + invoiceLiters.toPlainString() + " L"
                        + ", Remaining: " + (remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining.toPlainString() : "0") + " L";
            }
        }

        return null; // OK
    }

    /**
     * Checks all auto-block triggers after invoice creation:
     * 1. Amount exceeded: ledger balance > creditLimitAmount
     * 2. Liters exceeded: consumedLiters >= creditLimitLiters
     * 3. Aging 90+ days: any unpaid credit bill older than 90 days
     * Now records a CustomerBlockEvent with the reason.
     */
    @Transactional
    public void checkAndAutoBlock(Long customerId) {
        Customer customer = customerRepository.findByIdForUpdate(customerId).orElse(null);
        if (customer == null || customer.getStatus() != EntityStatus.ACTIVE) {
            return;
        }
        if (customer.isForceUnblocked()) {
            return;
        }

        String blockReason = null;

        // 1. Amount exceeded — check unbilled credit (current period purchases)
        if (customer.getCreditLimitAmount() != null && customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal unbilledCredit = invoiceBillRepository.sumUnbilledCreditByCustomer(customerId);
            if (unbilledCredit.compareTo(customer.getCreditLimitAmount()) > 0) {
                blockReason = "Credit limit exceeded. Unbilled: Rs." + unbilledCredit.toPlainString()
                        + ", Limit: Rs." + customer.getCreditLimitAmount().toPlainString();
            }
        }

        // 2. Liters exceeded
        if (blockReason == null && customer.getCreditLimitLiters() != null && customer.getConsumedLiters() != null
                && customer.getConsumedLiters().compareTo(customer.getCreditLimitLiters()) >= 0) {
            blockReason = "Liter limit exceeded. Consumed: " + customer.getConsumedLiters().toPlainString()
                    + " L, Limit: " + customer.getCreditLimitLiters().toPlainString() + " L";
        }

        // 3. Aging 90+ days
        if (blockReason == null) {
            LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
            if (invoiceBillRepository.existsUnpaidCreditBillBefore(customerId, ninetyDaysAgo)) {
                blockReason = "Unpaid credit bill older than 90 days";
            }
        }

        if (blockReason != null) {
            String previousStatus = customer.getStatus() != null ? customer.getStatus().name() : "ACTIVE";
            customer.setStatus(EntityStatus.BLOCKED);
            customer.setLastBlockedAt(LocalDateTime.now());
            customer.setBlockCount(customer.getBlockCount() != null ? customer.getBlockCount() + 1 : 1);
            customerRepository.save(customer);

            // Record block event
            com.stopforfuel.backend.entity.CustomerBlockEvent event = new com.stopforfuel.backend.entity.CustomerBlockEvent();
            event.setCustomer(customer);
            event.setScid(customer.getScid());
            event.setEventType("BLOCKED");
            event.setTriggerType("AUTO_INVOICE");
            event.setReason(blockReason);
            event.setPreviousStatus(previousStatus);
            blockEventRepository.save(event);
        }
    }

    /**
     * Updates customer credit limits (creditLimitAmount and/or creditLimitLiters).
     * Used by the "Set as Limit" workflow from statements.
     */
    @Transactional
    public Customer updateCreditLimits(Long id, Map<String, Object> limits) {
        Customer customer = getCustomerById(id);
        if (limits.containsKey("creditLimitAmount")) {
            Object val = limits.get("creditLimitAmount");
            customer.setCreditLimitAmount(val != null ? new BigDecimal(val.toString()) : null);
        }
        if (limits.containsKey("creditLimitLiters")) {
            Object val = limits.get("creditLimitLiters");
            customer.setCreditLimitLiters(val != null ? new BigDecimal(val.toString()) : null);
        }
        return customerRepository.save(customer);
    }

    /**
     * Returns credit limit info for a customer including current ledger balance.
     * Used by the invoice page for real-time credit limit validation.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCreditInfo(Long customerId) {
        Customer customer = getCustomerById(customerId);
        Map<String, Object> info = new HashMap<>();
        info.put("creditLimitAmount", customer.getCreditLimitAmount());
        info.put("creditLimitLiters", customer.getCreditLimitLiters());
        info.put("consumedLiters", customer.getConsumedLiters());

        // Calculate ledger balance
        BigDecimal totalBilled = invoiceBillRepository.sumAllCreditBillsByCustomer(customerId);
        BigDecimal totalPaid = paymentRepository.sumAllPaymentsByCustomer(customerId);
        BigDecimal ledgerBalance = totalBilled.subtract(totalPaid);
        info.put("ledgerBalance", ledgerBalance);
        info.put("totalBilled", totalBilled);
        info.put("totalPaid", totalPaid);

        // Unbilled credit (current period purchases not yet on a statement)
        BigDecimal unbilledCredit = invoiceBillRepository.sumUnbilledCreditByCustomer(customerId);
        info.put("unbilledCredit", unbilledCredit);

        return info;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        List<Customer> allCustomers = customerRepository.findAllByScid(SecurityUtils.getScid());
        long totalCustomers = allCustomers.size();
        long activeCustomers = allCustomers.stream()
                .filter(c -> c.getStatus() == EntityStatus.ACTIVE)
                .count();
        long blockedCustomers = allCustomers.stream()
                .filter(c -> c.getStatus() == EntityStatus.BLOCKED || c.getStatus() == EntityStatus.INACTIVE)
                .count();

        BigDecimal totalCreditGiven = allCustomers.stream()
                .map(c -> c.getCreditLimitLiters() != null ? c.getCreditLimitLiters() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalConsumed = allCustomers.stream()
                .map(c -> c.getConsumedLiters() != null ? c.getConsumedLiters() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double utilization = totalCreditGiven.compareTo(BigDecimal.ZERO) > 0
                ? totalConsumed.divide(totalCreditGiven, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100)).doubleValue()
                : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCustomers", totalCustomers);
        stats.put("activeFleets", activeCustomers);
        stats.put("blockedCustomers", blockedCustomers);
        stats.put("totalCreditGiven", totalCreditGiven);
        stats.put("totalConsumed", totalConsumed);
        stats.put("utilization", Math.round(utilization));

        return stats;
    }
}
