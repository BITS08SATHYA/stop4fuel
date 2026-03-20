package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.CashAdvance;
import com.stopforfuel.backend.service.CashAdvanceService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = CashAdvanceController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stopforfuel\\.config\\..*"))
class CashAdvanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CashAdvanceService cashAdvanceService;

    private CashAdvance testAdvance;

    @BeforeEach
    void setUp() {
        testAdvance = new CashAdvance();
        testAdvance.setId(1L);
        testAdvance.setAmount(new BigDecimal("5000"));
        testAdvance.setAdvanceType("REGULAR_ADVANCE");
        testAdvance.setRecipientName("Raj");
        testAdvance.setStatus("GIVEN");
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(cashAdvanceService.getAll()).thenReturn(List.of(testAdvance));

        mockMvc.perform(get("/api/advances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipientName").value("Raj"));
    }

    @Test
    void getByStatus_returnsList() throws Exception {
        when(cashAdvanceService.getByStatus("GIVEN")).thenReturn(List.of(testAdvance));

        mockMvc.perform(get("/api/advances/status/GIVEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("GIVEN"));
    }

    @Test
    void getByShift_returnsList() throws Exception {
        when(cashAdvanceService.getByShift(1L)).thenReturn(List.of(testAdvance));

        mockMvc.perform(get("/api/advances/shift/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(5000));
    }

    @Test
    void create_returnsCreatedAdvance() throws Exception {
        when(cashAdvanceService.create(any(CashAdvance.class))).thenReturn(testAdvance);

        mockMvc.perform(post("/api/advances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAdvance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Raj"));
    }

    @Test
    void recordReturn_returnsUpdatedAdvance() throws Exception {
        testAdvance.setStatus("RETURNED");
        when(cashAdvanceService.recordReturn(eq(1L), any(BigDecimal.class), anyString()))
                .thenReturn(testAdvance);

        Map<String, Object> body = Map.of(
                "returnedAmount", "5000",
                "returnRemarks", "Full return");

        mockMvc.perform(post("/api/advances/1/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
    }

    @Test
    void cancel_returnsCancelledAdvance() throws Exception {
        testAdvance.setStatus("CANCELLED");
        when(cashAdvanceService.cancel(1L)).thenReturn(testAdvance);

        mockMvc.perform(patch("/api/advances/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        doNothing().when(cashAdvanceService).delete(1L);

        mockMvc.perform(delete("/api/advances/1"))
                .andExpect(status().isNoContent());

        verify(cashAdvanceService).delete(1L);
    }
}
