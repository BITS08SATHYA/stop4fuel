package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.service.MappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MappingController.class)
class MappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MappingService mappingService;

    private Customer testCustomer;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");

        testVehicle = new Vehicle();
        testVehicle.setId(1L);
        testVehicle.setVehicleNumber("TN01AB1234");
    }

    @Test
    void getUnassignedCustomers_returnsList() throws Exception {
        when(mappingService.getUnassignedCustomers()).thenReturn(List.of(testCustomer));

        mockMvc.perform(get("/api/mappings/unassigned-customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Customer"));
    }

    @Test
    void getUnassignedVehicles_returnsList() throws Exception {
        when(mappingService.getUnassignedVehicles()).thenReturn(List.of(testVehicle));

        mockMvc.perform(get("/api/mappings/unassigned-vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleNumber").value("TN01AB1234"));
    }

    @Test
    void assignCustomersToGroup_returnsAssignedCustomers() throws Exception {
        when(mappingService.assignCustomersToGroup(anyList(), eq(1L)))
                .thenReturn(List.of(testCustomer));

        Map<String, Object> body = Map.of("customerIds", List.of(1), "groupId", 1);

        mockMvc.perform(patch("/api/mappings/assign-customers-to-group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Customer"));
    }

    @Test
    void unassignCustomersFromGroup_returnsList() throws Exception {
        when(mappingService.unassignCustomersFromGroup(anyList()))
                .thenReturn(List.of(testCustomer));

        Map<String, Object> body = Map.of("customerIds", List.of(1));

        mockMvc.perform(patch("/api/mappings/unassign-customers-from-group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Customer"));
    }

    @Test
    void assignVehiclesToCustomer_returnsList() throws Exception {
        when(mappingService.assignVehiclesToCustomer(anyList(), eq(1L)))
                .thenReturn(List.of(testVehicle));

        Map<String, Object> body = Map.of("vehicleIds", List.of(1), "customerId", 1);

        mockMvc.perform(patch("/api/mappings/assign-vehicles-to-customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleNumber").value("TN01AB1234"));
    }

    @Test
    void unassignVehiclesFromCustomer_returnsList() throws Exception {
        when(mappingService.unassignVehiclesFromCustomer(anyList()))
                .thenReturn(List.of(testVehicle));

        Map<String, Object> body = Map.of("vehicleIds", List.of(1));

        mockMvc.perform(patch("/api/mappings/unassign-vehicles-from-customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleNumber").value("TN01AB1234"));
    }
}
