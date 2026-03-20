package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.CashInflowRepayment;
import com.stopforfuel.backend.entity.ExternalCashInflow;
import com.stopforfuel.backend.service.ExternalCashInflowService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ExternalCashInflowController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stopforfuel\\.config\\..*"))
class ExternalCashInflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExternalCashInflowService service;

    private ExternalCashInflow testInflow;
    private CashInflowRepayment testRepayment;

    @BeforeEach
    void setUp() {
        testInflow = new ExternalCashInflow();
        testInflow.setId(1L);
        testInflow.setAmount(new BigDecimal("10000"));
        testInflow.setSource("Owner");
        testInflow.setPurpose("Working capital");
        testInflow.setStatus("ACTIVE");
        testInflow.setRepaidAmount(BigDecimal.ZERO);
        testInflow.setInflowDate(LocalDateTime.now());

        testRepayment = new CashInflowRepayment();
        testRepayment.setId(1L);
        testRepayment.setAmount(new BigDecimal("3000"));
        testRepayment.setRemarks("Partial repayment");
        testRepayment.setRepaymentDate(LocalDateTime.now());
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(service.getAll()).thenReturn(List.of(testInflow));

        mockMvc.perform(get("/api/cash-inflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("Owner"))
                .andExpect(jsonPath("$[0].amount").value(10000))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void getAll_emptyList() throws Exception {
        when(service.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/cash-inflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getByShift_returnsList() throws Exception {
        when(service.getByShift(1L)).thenReturn(List.of(testInflow));

        mockMvc.perform(get("/api/cash-inflows/shift/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getByStatus_returnsList() throws Exception {
        when(service.getByStatus("ACTIVE")).thenReturn(List.of(testInflow));

        mockMvc.perform(get("/api/cash-inflows/status/ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void create_returnsCreatedInflow() throws Exception {
        when(service.create(any(ExternalCashInflow.class))).thenReturn(testInflow);

        mockMvc.perform(post("/api/cash-inflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testInflow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("Owner"))
                .andExpect(jsonPath("$.amount").value(10000))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void recordRepayment_returnsRepayment() throws Exception {
        when(service.recordRepayment(eq(1L), any(CashInflowRepayment.class))).thenReturn(testRepayment);

        mockMvc.perform(post("/api/cash-inflows/1/repay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRepayment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(3000))
                .andExpect(jsonPath("$.remarks").value("Partial repayment"));
    }

    @Test
    void getRepayments_returnsList() throws Exception {
        when(service.getRepayments(1L)).thenReturn(List.of(testRepayment));

        mockMvc.perform(get("/api/cash-inflows/1/repayments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(3000));
    }

    @Test
    void getRepayments_emptyList() throws Exception {
        when(service.getRepayments(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/cash-inflows/1/repayments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
