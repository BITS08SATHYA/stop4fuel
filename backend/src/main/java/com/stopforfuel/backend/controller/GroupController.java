package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.GroupDTO;
import com.stopforfuel.backend.dto.CustomerListDTO;
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
    public List<GroupDTO> getAllGroups() {
        return groupService.getAllGroups().stream().map(GroupDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_CREATE')")
    public GroupDTO createGroup(@Valid @RequestBody Group group) {
        return GroupDTO.from(groupService.createGroup(group));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public GroupDTO updateGroup(@PathVariable Long id, @Valid @RequestBody Group group) {
        return GroupDTO.from(groupService.updateGroup(id, group));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_DELETE')")
    public void deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
    }

    @GetMapping("/{id}/customers")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public Page<CustomerListDTO> getCustomersByGroupId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return customerService.getCustomersByGroupId(id, PageRequest.of(page, Math.min(size, 100)))
                .map(CustomerListDTO::from);
    }
}
