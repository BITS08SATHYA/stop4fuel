package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.service.ShiftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ShiftController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stopforfuel\\.config\\..*"))
class ShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShiftService shiftService;

    private Shift testShift;

    @BeforeEach
    void setUp() {
        testShift = new Shift();
        testShift.setId(1L);
        testShift.setStatus("OPEN");
        testShift.setStartTime(LocalDateTime.of(2026, 3, 17, 8, 0));
        testShift.setScid(1L);
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(shiftService.getAllShifts()).thenReturn(List.of(testShift));

        mockMvc.perform(get("/api/shifts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void getActive_returnsOpenShift() throws Exception {
        when(shiftService.getActiveShift()).thenReturn(testShift);

        mockMvc.perform(get("/api/shifts/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getActive_noOpenShift_returnsNull() throws Exception {
        when(shiftService.getActiveShift()).thenReturn(null);

        mockMvc.perform(get("/api/shifts/active"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void open_createsNewShift() throws Exception {
        Shift newShift = new Shift();
        newShift.setScid(1L);

        when(shiftService.openShift(any(Shift.class))).thenReturn(testShift);

        String json = objectMapper.writeValueAsString(newShift);

        mockMvc.perform(post("/api/shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void open_existingOpenShift_returns500() throws Exception {
        when(shiftService.openShift(any(Shift.class)))
                .thenThrow(new RuntimeException("A shift is already open"));

        Shift newShift = new Shift();
        String json = objectMapper.writeValueAsString(newShift);

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/shifts/open")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andExpect(status().is5xxServerError()));
    }

    @Test
    void close_closesShift() throws Exception {
        testShift.setStatus("CLOSED");
        testShift.setEndTime(LocalDateTime.of(2026, 3, 17, 20, 0));
        when(shiftService.closeShift(1L)).thenReturn(testShift);

        mockMvc.perform(post("/api/shifts/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void close_shiftNotFound_returns500() throws Exception {
        when(shiftService.closeShift(99L))
                .thenThrow(new RuntimeException("Shift not found"));

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/shifts/99/close"))
                        .andExpect(status().is5xxServerError()));
    }
}
