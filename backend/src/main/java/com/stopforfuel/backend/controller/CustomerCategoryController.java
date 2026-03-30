package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.CustomerCategoryDTO;
import com.stopforfuel.backend.entity.CustomerCategory;
import com.stopforfuel.backend.service.CustomerCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-categories")
public class CustomerCategoryController {

    @Autowired
    private CustomerCategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<CustomerCategory> getAll() {
        return categoryService.getAllCategories();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public CustomerCategory getById(@PathVariable Long id) {
        return categoryService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerCategory create(@Valid @RequestBody CustomerCategory category) {
        return categoryService.createCategory(category);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public CustomerCategory update(@PathVariable Long id, @Valid @RequestBody CustomerCategory category) {
        return categoryService.updateCategory(id, category);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public void delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }
}
