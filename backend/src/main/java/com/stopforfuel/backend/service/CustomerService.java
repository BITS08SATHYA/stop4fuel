package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InvoiceBillRepository invoiceBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.stopforfuel.backend.repository.RolesRepository rolesRepository;

    public org.springframework.data.domain.Page<Customer> getCustomers(String search, Long groupId, String status, String categoryType, org.springframework.data.domain.Pageable pageable) {
        String cat = (categoryType != null && !categoryType.isEmpty()) ? categoryType : null;
        if (search != null && !search.isEmpty()) {
            return customerRepository.findBySearchAndFilters(search, groupId, status, cat, pageable);
        }
        return customerRepository.findByGroupAndStatus(groupId, status, cat, pageable);
    }

    public org.springframework.data.domain.Page<Customer> getCustomersByGroupId(Long groupId, org.springframework.data.domain.Pageable pageable) {
        return customerRepository.findByGroupId(groupId, pageable);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAllByScid(SecurityUtils.getScid());
    }

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
            customer.setStatus("ACTIVE");
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
        String current = customer.getStatus();
        if (current == null || "ACTIVE".equals(current)) {
            customer.setStatus("INACTIVE");
        } else {
            // Both INACTIVE and BLOCKED can be manually set back to ACTIVE
            customer.setStatus("ACTIVE");
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
        Customer customer = getCustomerById(id);
        if (!"ACTIVE".equals(customer.getStatus())) {
            throw new BusinessException("Customer can only be blocked when ACTIVE. Current status: " + customer.getStatus());
        }
        customer.setStatus("BLOCKED");
        return customerRepository.save(customer);
    }

    /**
     * Unblock a customer (only if currently BLOCKED).
     */
    @Transactional
    public Customer unblockCustomer(Long id) {
        Customer customer = getCustomerById(id);
        if (!"BLOCKED".equals(customer.getStatus())) {
            throw new BusinessException("Customer can only be unblocked when BLOCKED. Current status: " + customer.getStatus());
        }
        customer.setStatus("ACTIVE");
        return customerRepository.save(customer);
    }

    /**
     * Pre-invoice validation: checks if a new credit invoice would exceed the customer's limits.
     * Returns null if OK, or an error message string if limit would be breached.
     */
    public String validateCreditLimitBeforeInvoice(Long customerId, BigDecimal invoiceAmount, BigDecimal invoiceLiters) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) return null;

        // 1. Amount-based check: would new invoice push ledger balance beyond creditLimitAmount?
        if (customer.getCreditLimitAmount() != null && customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) > 0
                && invoiceAmount != null) {
            BigDecimal totalBilled = invoiceBillRepository.sumAllCreditBillsByCustomer(customerId);
            BigDecimal totalPaid = paymentRepository.sumAllPaymentsByCustomer(customerId);
            BigDecimal currentBalance = totalBilled.subtract(totalPaid);
            BigDecimal projectedBalance = currentBalance.add(invoiceAmount);
            if (projectedBalance.compareTo(customer.getCreditLimitAmount()) > 0) {
                BigDecimal remaining = customer.getCreditLimitAmount().subtract(currentBalance);
                return "Customer '" + customer.getName() + "' credit limit would be exceeded. "
                        + "Limit: ₹" + customer.getCreditLimitAmount().toPlainString()
                        + ", Current balance: ₹" + currentBalance.toPlainString()
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
     */
    @Transactional
    public void checkAndAutoBlock(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null || !"ACTIVE".equals(customer.getStatus())) {
            return;
        }

        // 1. Amount exceeded
        if (customer.getCreditLimitAmount() != null && customer.getCreditLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalBilled = invoiceBillRepository.sumAllCreditBillsByCustomer(customerId);
            BigDecimal totalPaid = paymentRepository.sumAllPaymentsByCustomer(customerId);
            BigDecimal ledgerBalance = totalBilled.subtract(totalPaid);
            if (ledgerBalance.compareTo(customer.getCreditLimitAmount()) > 0) {
                customer.setStatus("BLOCKED");
                customerRepository.save(customer);
                return;
            }
        }

        // 2. Liters exceeded (safety net for existing inline check)
        if (customer.getCreditLimitLiters() != null && customer.getConsumedLiters() != null
                && customer.getConsumedLiters().compareTo(customer.getCreditLimitLiters()) >= 0) {
            customer.setStatus("BLOCKED");
            customerRepository.save(customer);
            return;
        }

        // 3. Aging 90+ days
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        if (invoiceBillRepository.existsUnpaidCreditBillBefore(customerId, ninetyDaysAgo)) {
            customer.setStatus("BLOCKED");
            customerRepository.save(customer);
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

        return info;
    }

    public Map<String, Object> getStats() {
        List<Customer> allCustomers = customerRepository.findAllByScid(SecurityUtils.getScid());
        long totalCustomers = allCustomers.size();
        long activeCustomers = allCustomers.stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .count();
        long blockedCustomers = allCustomers.stream()
                .filter(c -> "BLOCKED".equals(c.getStatus()) || "INACTIVE".equals(c.getStatus()))
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
