package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.GroupRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MappingService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private GroupRepository groupRepository;

    public List<Customer> getUnassignedCustomers() {
        return customerRepository.findByGroupIsNull();
    }

    public List<Vehicle> getUnassignedVehicles() {
        return vehicleRepository.findByCustomerIsNull();
    }

    @Transactional
    public List<Customer> assignCustomersToGroup(List<Long> customerIds, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found with id: " + groupId));
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
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        List<Vehicle> vehicles = vehicleRepository.findByIdIn(vehicleIds);
        for (Vehicle vehicle : vehicles) {
            vehicle.setCustomer(customer);
        }
        return vehicleRepository.saveAll(vehicles);
    }

    @Transactional
    public List<Vehicle> unassignVehiclesFromCustomer(List<Long> vehicleIds) {
        List<Vehicle> vehicles = vehicleRepository.findByIdIn(vehicleIds);
        for (Vehicle vehicle : vehicles) {
            vehicle.setCustomer(null);
        }
        return vehicleRepository.saveAll(vehicles);
    }
}
