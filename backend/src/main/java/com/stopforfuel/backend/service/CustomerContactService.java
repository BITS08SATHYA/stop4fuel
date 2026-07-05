package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.CustomerContact;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerContactRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerContactService {

    private final CustomerContactRepository repository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerContact> getByCustomer(Long customerId) {
        return repository.findByCustomerId(customerId, SecurityUtils.getScid());
    }

    @Transactional
    public CustomerContact create(Long customerId, CustomerContact contact) {
        Long scid = SecurityUtils.getScid();
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> scid.equals(c.getScid()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        contact.setCustomer(customer);
        contact.setScid(scid);
        return repository.save(contact);
    }

    @Transactional
    public CustomerContact update(Long id, CustomerContact details) {
        CustomerContact contact = repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + id));
        contact.setName(details.getName());
        contact.setContactRole(details.getContactRole());
        contact.setPhoneNumber(details.getPhoneNumber());
        contact.setNotes(details.getNotes());
        return repository.save(contact);
    }

    @Transactional
    public void delete(Long id) {
        CustomerContact contact = repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + id));
        repository.delete(contact);
    }
}
