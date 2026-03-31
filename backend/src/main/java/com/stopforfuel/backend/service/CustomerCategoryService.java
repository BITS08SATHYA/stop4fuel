package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.CustomerCategory;
import com.stopforfuel.backend.exception.DuplicateResourceException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerCategoryRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerCategoryService {

    private final CustomerCategoryRepository categoryRepository;

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CustomerCategory getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer category not found with id: " + id));
    }

    public CustomerCategory createCategory(CustomerCategory category) {
        categoryRepository.findByCategoryName(category.getCategoryName()).ifPresent(existing -> {
            throw new DuplicateResourceException("Category with name '" + category.getCategoryName() + "' already exists");
        });
        return categoryRepository.save(category);
    }

    public CustomerCategory updateCategory(Long id, CustomerCategory details) {
        CustomerCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer category not found with id: " + id));
        category.setCategoryName(details.getCategoryName());
        category.setCategoryType(details.getCategoryType());
        category.setDescription(details.getDescription());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        CustomerCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer category not found with id: " + id));
        List<Customer> customers = category.getCustomers();
        for (Customer customer : customers) {
            customer.setCustomerCategory(null);
        }
        customerRepository.saveAll(customers);
        categoryRepository.delete(category);
    }
}
