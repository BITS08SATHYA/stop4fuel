package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
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

    public org.springframework.data.domain.Page<Customer> getCustomers(String search, Long groupId, String status, String customerCategory, org.springframework.data.domain.Pageable pageable) {
        String cat = (customerCategory != null && !customerCategory.isEmpty()) ? customerCategory : null;
        if (search != null && !search.isEmpty()) {
            return customerRepository.findBySearchAndFilters(search, groupId, status, cat, pageable);
        }
        return customerRepository.findByGroupAndStatus(groupId, status, cat, pageable);
    }

    public org.springframework.data.domain.Page<Customer> getCustomersByGroupId(Long groupId, org.springframework.data.domain.Pageable pageable) {
        return customerRepository.findByGroupId(groupId, pageable);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
    }

    public Customer createCustomer(Customer customer) {
        if (customer.getRole() == null) {
            com.stopforfuel.backend.entity.Roles customerRole = rolesRepository.findByRoleType("CUSTOMER")
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
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
        customer.setStatementThresholdAmount(customerDetails.getStatementThresholdAmount());

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
            throw new RuntimeException("Customer can only be blocked when ACTIVE. Current status: " + customer.getStatus());
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
            throw new RuntimeException("Customer can only be unblocked when BLOCKED. Current status: " + customer.getStatus());
        }
        customer.setStatus("ACTIVE");
        return customerRepository.save(customer);
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

    public Map<String, Object> getStats() {
        List<Customer> allCustomers = customerRepository.findAll();
        long totalCustomers = allCustomers.size();
        long activeCustomers = allCustomers.stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .count();
        long blockedCustomers = allCustomers.stream()
                .filter(c -> "BLOCKED".equals(c.getStatus()))
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
