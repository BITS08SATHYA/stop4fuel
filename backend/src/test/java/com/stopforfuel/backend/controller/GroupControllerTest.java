package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.service.CustomerService;
import com.stopforfuel.backend.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupController.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupService groupService;

    @MockBean
    private CustomerService customerService;

    private Group testGroup;

    @BeforeEach
    void setUp() {
        testGroup = new Group();
        testGroup.setId(1L);
        testGroup.setGroupName("Fleet A");
    }

    @Test
    void getAllGroups_returnsList() throws Exception {
        when(groupService.getAllGroups()).thenReturn(List.of(testGroup));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupName").value("Fleet A"));
    }

    @Test
    void createGroup_returnsCreatedGroup() throws Exception {
        when(groupService.createGroup(any(Group.class))).thenReturn(testGroup);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testGroup)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("Fleet A"));
    }

    @Test
    void updateGroup_returnsUpdatedGroup() throws Exception {
        testGroup.setGroupName("Updated Fleet");
        when(groupService.updateGroup(eq(1L), any(Group.class))).thenReturn(testGroup);

        mockMvc.perform(put("/api/groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testGroup)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("Updated Fleet"));
    }

    @Test
    void deleteGroup_returnsOk() throws Exception {
        doNothing().when(groupService).deleteGroup(1L);

        mockMvc.perform(delete("/api/groups/1"))
                .andExpect(status().isOk());

        verify(groupService).deleteGroup(1L);
    }

    @Test
    void getCustomersByGroupId_returnsPage() throws Exception {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Test Customer");
        when(customerService.getCustomersByGroupId(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        mockMvc.perform(get("/api/groups/1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Customer"));
    }
}
