package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.service.CustomerService;
import com.stopforfuel.backend.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<Group> getAllGroups() {
        return groupService.getAllGroups();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public Group createGroup(@Valid @RequestBody Group group) {
        return groupService.createGroup(group);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public Group updateGroup(@PathVariable Long id, @Valid @RequestBody Group group) {
        return groupService.updateGroup(id, group);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_MANAGE')")
    public void deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
    }

    @GetMapping("/{id}/customers")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public Page<Customer> getCustomersByGroupId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return customerService.getCustomersByGroupId(id, PageRequest.of(page, size));
    }
}
