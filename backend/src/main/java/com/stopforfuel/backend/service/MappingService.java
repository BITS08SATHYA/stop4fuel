package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.GroupRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import com.stopforfuel.config.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MappingService {

    private static final Logger log = LoggerFactory.getLogger(MappingService.class);

    private final CustomerRepository customerRepository;

    private final VehicleRepository vehicleRepository;

    private final GroupRepository groupRepository;

    private final InvoiceBillRepository invoiceBillRepository;

    @Transactional(readOnly = true)
    public List<Customer> getUnassignedCustomers() {
        return customerRepository.findByGroupIsNull();
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getUnassignedVehicles() {
        return vehicleRepository.findByCustomerIsNull();
    }

    @Transactional
    public List<Customer> assignCustomersToGroup(List<Long> customerIds, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));
        List<Customer> customers = customerRepository.findByIdIn(customerIds);
        for (Customer customer : customers) {
            customer.setGroup(group);
        }
        return customerRepository.saveAll(customers);
    }

    @Transactional
    public List<Customer> unassignCustomersFromGroup(List<Long> customerIds) {
        List<Customer> customers = customerRepository.findByIdIn(customerIds);
        for (Customer customer : customers) {
            customer.setGroup(null);
        }
        return customerRepository.saveAll(customers);
    }

    @Transactional
    public List<Vehicle> assignVehiclesToCustomer(List<Long> vehicleIds, Long customerId) {
        Customer customer = customerRepository.findByIdAndScid(customerId, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        List<Vehicle> vehicles = vehicleRepository.findByIdIn(vehicleIds);
        for (Vehicle vehicle : vehicles) {
            Customer oldCustomer = vehicle.getCustomer();
            if (oldCustomer != null && !oldCustomer.getId().equals(customerId)) {
                // Check for unpaid credit bills before reassigning
                long unpaidCount = invoiceBillRepository.countByVehicleIdAndCustomerIdAndPaymentStatus(
                        vehicle.getId(), oldCustomer.getId(), "NOT_PAID");
                if (unpaidCount > 0) {
                    throw new BusinessException(
                            "Vehicle " + vehicle.getVehicleNumber() + " has " + unpaidCount +
                            " unpaid credit bill(s) under " + oldCustomer.getName() +
                            ". Settle them before reassigning.");
                }

                // Deduct vehicle's consumed liters from old customer's total
                BigDecimal vehicleConsumed = vehicle.getConsumedLiters() != null ? vehicle.getConsumedLiters() : BigDecimal.ZERO;
                if (vehicleConsumed.compareTo(BigDecimal.ZERO) > 0 && oldCustomer.getConsumedLiters() != null) {
                    oldCustomer.setConsumedLiters(oldCustomer.getConsumedLiters().subtract(vehicleConsumed));
                    customerRepository.save(oldCustomer);
                }

                log.info("Vehicle {} reassigned from customer {} (id={}) to {} (id={}). Consumed liters reset from {}.",
                        vehicle.getVehicleNumber(), oldCustomer.getName(), oldCustomer.getId(),
                        customer.getName(), customer.getId(), vehicleConsumed);
            }

            // Reset consumed liters for the new assignment cycle
            vehicle.setConsumedLiters(BigDecimal.ZERO);
            vehicle.setStatus("ACTIVE");
            vehicle.setCustomer(customer);
        }
        return vehicleRepository.saveAll(vehicles);
    }

    @Transactional
    public List<Vehicle> unassignVehiclesFromCustomer(List<Long> vehicleIds) {
        List<Vehicle> vehicles = vehicleRepository.findByIdIn(vehicleIds);
        for (Vehicle vehicle : vehicles) {
            Customer oldCustomer = vehicle.getCustomer();
            if (oldCustomer != null) {
                // Check for unpaid credit bills
                long unpaidCount = invoiceBillRepository.countByVehicleIdAndCustomerIdAndPaymentStatus(
                        vehicle.getId(), oldCustomer.getId(), "NOT_PAID");
                if (unpaidCount > 0) {
                    throw new BusinessException(
                            "Vehicle " + vehicle.getVehicleNumber() + " has " + unpaidCount +
                            " unpaid credit bill(s). Settle them before unassigning.");
                }

                // Deduct from customer's consumed liters
                BigDecimal vehicleConsumed = vehicle.getConsumedLiters() != null ? vehicle.getConsumedLiters() : BigDecimal.ZERO;
                if (vehicleConsumed.compareTo(BigDecimal.ZERO) > 0 && oldCustomer.getConsumedLiters() != null) {
                    oldCustomer.setConsumedLiters(oldCustomer.getConsumedLiters().subtract(vehicleConsumed));
                    customerRepository.save(oldCustomer);
                }

                log.info("Vehicle {} unassigned from customer {} (id={}). Consumed liters were {}.",
                        vehicle.getVehicleNumber(), oldCustomer.getName(), oldCustomer.getId(), vehicleConsumed);
            }

            vehicle.setConsumedLiters(BigDecimal.ZERO);
            vehicle.setCustomer(null);
        }
        return vehicleRepository.saveAll(vehicles);
    }
}
