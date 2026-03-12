package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.GroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GroupService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CustomerRepository customerRepository;

    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    public Group createGroup(Group group) {
        return groupRepository.save(group);
    }

    public Group updateGroup(Long id, Group groupDetails) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found with id: " + id));
        group.setGroupName(groupDetails.getGroupName());
        group.setDescription(groupDetails.getDescription());
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long id) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found with id: " + id));
        // Unlink all customers from this group before deleting
        List<Customer> customers = group.getCustomers();
        for (Customer customer : customers) {
            customer.setGroup(null);
        }
        customerRepository.saveAll(customers);
        groupRepository.delete(group);
    }
}
