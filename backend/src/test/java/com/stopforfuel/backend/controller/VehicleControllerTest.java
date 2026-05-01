package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.EntityStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = VehicleController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stopforfuel\\.config\\..*"))
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VehicleService vehicleService;

    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testVehicle = new Vehicle();
        testVehicle.setId(1L);
        testVehicle.setVehicleNumber("TN01AB1234");
        testVehicle.setStatus(com.stopforfuel.backend.enums.EntityStatus.ACTIVE);
    }

    @Test
    void getAllVehicles_returnsPagedList() throws Exception {
        Page<Vehicle> page = new PageImpl<>(List.of(testVehicle));
        // service is called with raw request params — search is null when not provided
        when(vehicleService.searchPaged(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].vehicleNumber").value("TN01AB1234"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllVehicles_withSearch() throws Exception {
        Page<Vehicle> page = new PageImpl<>(List.of(testVehicle));
        when(vehicleService.searchPaged(eq("TN01"), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/vehicles").param("search", "TN01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].vehicleNumber").value("TN01AB1234"));
    }

    @Test
    void getVehiclesByCustomerId_returnsList() throws Exception {
        when(vehicleService.getVehiclesByCustomerId(1L)).thenReturn(List.of(testVehicle));

        mockMvc.perform(get("/api/vehicles/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleNumber").value("TN01AB1234"));
    }

    @Test
    void createVehicle_returnsCreatedVehicle() throws Exception {
        when(vehicleService.createVehicle(any(Vehicle.class))).thenReturn(testVehicle);

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testVehicle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleNumber").value("TN01AB1234"));
    }

    @Test
    void updateVehicle_returnsUpdatedVehicle() throws Exception {
        testVehicle.setVehicleNumber("TN99ZZ9999");
        when(vehicleService.updateVehicle(eq(1L), any(Vehicle.class))).thenReturn(testVehicle);

        mockMvc.perform(put("/api/vehicles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testVehicle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleNumber").value("TN99ZZ9999"));
    }

    @Test
    void deleteVehicle_returnsOk() throws Exception {
        doNothing().when(vehicleService).deleteVehicle(1L);

        mockMvc.perform(delete("/api/vehicles/1"))
                .andExpect(status().isOk());

        verify(vehicleService).deleteVehicle(1L);
    }

    @Test
    void toggleStatus_returnsToggledVehicle() throws Exception {
        testVehicle.setStatus(com.stopforfuel.backend.enums.EntityStatus.INACTIVE);
        when(vehicleService.toggleStatus(1L)).thenReturn(testVehicle);

        mockMvc.perform(patch("/api/vehicles/1/toggle-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // --- searchVehicles endpoint tests ---

    @Test
    void searchVehicles_returnsMatchingVehicles() throws Exception {
        when(vehicleService.searchVehicles("TN01", null)).thenReturn(List.of(testVehicle));

        mockMvc.perform(get("/api/vehicles/search").param("q", "TN01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleNumber").value("TN01AB1234"));
    }

    @Test
    void searchVehicles_emptyResults() throws Exception {
        when(vehicleService.searchVehicles("XYZ", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/vehicles/search").param("q", "XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void searchVehicles_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/api/vehicles/search"))
                .andExpect(status().isBadRequest());
    }
}
