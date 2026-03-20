package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.service.CustomerService;
import com.stopforfuel.backend.service.JasperReportService;
import com.stopforfuel.backend.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = CustomerController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stopforfuel\\.config\\..*"))
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private VehicleService vehicleService;

    @MockBean
    private JasperReportService jasperReportService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        Roles role = new Roles();
        role.setId(1L);
        role.setRoleType("CUSTOMER");

        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");
        testCustomer.setUsername("testcustomer");
        testCustomer.setStatus("ACTIVE");
        testCustomer.setRole(role);
        testCustomer.setConsumedLiters(BigDecimal.ZERO);
        testCustomer.setCreditLimitAmount(new BigDecimal("50000"));
    }

    @Test
    void getCustomers_returnsPagedResults() throws Exception {
        when(customerService.getCustomers(isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testCustomer)));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Customer"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getCustomers_withSearchParam() throws Exception {
        when(customerService.getCustomers(eq("test"), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testCustomer)));

        mockMvc.perform(get("/api/customers").param("search", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Customer"));
    }

    @Test
    void getCustomers_withGroupIdParam() throws Exception {
        when(customerService.getCustomers(isNull(), eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testCustomer)));

        mockMvc.perform(get("/api/customers").param("groupId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Customer"));
    }

    @Test
    void getCustomerById_returnsCustomer() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(testCustomer);

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Customer"))
                .andExpect(jsonPath("$.username").value("testcustomer"));
    }

    @Test
    void getCustomerById_notFound_returns500() throws Exception {
        when(customerService.getCustomerById(99L))
                .thenThrow(new RuntimeException("Customer not found with id: 99"));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void createCustomer_returnsCreatedCustomer() throws Exception {
        when(customerService.createCustomer(any(Customer.class))).thenReturn(testCustomer);

        String json = objectMapper.writeValueAsString(testCustomer);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Customer"));
    }

    @Test
    void updateCustomer_returnsUpdatedCustomer() throws Exception {
        testCustomer.setName("Updated Name");
        when(customerService.updateCustomer(eq(1L), any(Customer.class))).thenReturn(testCustomer);

        String json = objectMapper.writeValueAsString(testCustomer);

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void deleteCustomer_returnsOk() throws Exception {
        doNothing().when(customerService).deleteCustomer(1L);

        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isOk());

        verify(customerService).deleteCustomer(1L);
    }

    @Test
    void getStats_returnsStatsMap() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCustomers", 10L);
        stats.put("activeFleets", 8L);
        stats.put("blockedCustomers", 2L);
        when(customerService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/customers/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").value(10))
                .andExpect(jsonPath("$.activeFleets").value(8))
                .andExpect(jsonPath("$.blockedCustomers").value(2));
    }

    @Test
    void toggleStatus_returnsToggledCustomer() throws Exception {
        testCustomer.setStatus("INACTIVE");
        when(customerService.toggleStatus(1L)).thenReturn(testCustomer);

        mockMvc.perform(patch("/api/customers/1/toggle-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void blockCustomer_returnsBlockedCustomer() throws Exception {
        testCustomer.setStatus("BLOCKED");
        when(customerService.blockCustomer(1L)).thenReturn(testCustomer);

        mockMvc.perform(patch("/api/customers/1/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void unblockCustomer_returnsUnblockedCustomer() throws Exception {
        testCustomer.setStatus("ACTIVE");
        when(customerService.unblockCustomer(1L)).thenReturn(testCustomer);

        mockMvc.perform(patch("/api/customers/1/unblock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getVehiclesByCustomerId_returnsList() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setVehicleNumber("TN01AB1234");
        vehicle.setStatus("ACTIVE");

        when(vehicleService.getVehiclesByCustomerId(1L)).thenReturn(List.of(vehicle));

        mockMvc.perform(get("/api/customers/1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleNumber").value("TN01AB1234"));
    }
}
