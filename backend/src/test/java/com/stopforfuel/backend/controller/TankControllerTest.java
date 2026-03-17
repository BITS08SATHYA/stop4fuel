package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.service.TankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TankController.class)
class TankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TankService tankService;

    private Tank testTank;

    @BeforeEach
    void setUp() {
        testTank = new Tank();
        testTank.setId(1L);
        testTank.setName("Tank A");
        testTank.setCapacity(10000.0);
        testTank.setActive(true);
    }

    @Test
    void getAllTanks_returnsList() throws Exception {
        when(tankService.getAllTanks()).thenReturn(List.of(testTank));

        mockMvc.perform(get("/api/tanks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tank A"));
    }

    @Test
    void getActiveTanks_returnsList() throws Exception {
        when(tankService.getActiveTanks()).thenReturn(List.of(testTank));

        mockMvc.perform(get("/api/tanks/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void getTanksByProduct_returnsList() throws Exception {
        when(tankService.getTanksByProduct(1L)).thenReturn(List.of(testTank));

        mockMvc.perform(get("/api/tanks/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tank A"));
    }

    @Test
    void getTankById_returnsTank() throws Exception {
        when(tankService.getTankById(1L)).thenReturn(testTank);

        mockMvc.perform(get("/api/tanks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tank A"));
    }

    @Test
    void createTank_returnsCreatedTank() throws Exception {
        when(tankService.createTank(any(Tank.class))).thenReturn(testTank);

        mockMvc.perform(post("/api/tanks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testTank)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tank A"));
    }

    @Test
    void updateTank_returnsUpdatedTank() throws Exception {
        testTank.setName("Updated Tank");
        when(tankService.updateTank(eq(1L), any(Tank.class))).thenReturn(testTank);

        mockMvc.perform(put("/api/tanks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testTank)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Tank"));
    }

    @Test
    void toggleStatus_returnsToggledTank() throws Exception {
        testTank.setActive(false);
        when(tankService.toggleStatus(1L)).thenReturn(testTank);

        mockMvc.perform(patch("/api/tanks/1/toggle-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteTank_returnsOk() throws Exception {
        doNothing().when(tankService).deleteTank(1L);

        mockMvc.perform(delete("/api/tanks/1"))
                .andExpect(status().isOk());

        verify(tankService).deleteTank(1L);
    }
}
