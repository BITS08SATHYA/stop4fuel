package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.stopforfuel.backend.entity.PaymentMode;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        PaymentMode paymentMode = new PaymentMode();
        paymentMode.setId(1L);
        paymentMode.setModeName("CASH");

        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setAmount(new BigDecimal("10000"));
        testPayment.setPaymentMode(paymentMode);
    }

    @Test
    void getAll_returnsPage() throws Exception {
        when(paymentService.getPayments(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testPayment)));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(10000));
    }

    @Test
    void getById_returnsPayment() throws Exception {
        when(paymentService.getPaymentById(1L)).thenReturn(testPayment);

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10000));
    }

    @Test
    void getByCustomer_returnsPage() throws Exception {
        when(paymentService.getPaymentsByCustomer(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testPayment)));

        mockMvc.perform(get("/api/payments/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(10000));
    }

    @Test
    void getByStatement_returnsList() throws Exception {
        when(paymentService.getPaymentsByStatement(1L)).thenReturn(List.of(testPayment));

        mockMvc.perform(get("/api/payments/statement/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(10000));
    }

    @Test
    void getByInvoiceBill_returnsList() throws Exception {
        when(paymentService.getPaymentsByInvoiceBill(1L)).thenReturn(List.of(testPayment));

        mockMvc.perform(get("/api/payments/bill/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(10000));
    }

    @Test
    void getByShift_returnsList() throws Exception {
        when(paymentService.getPaymentsByShift(1L)).thenReturn(List.of(testPayment));

        mockMvc.perform(get("/api/payments/shift/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(10000));
    }

    @Test
    void recordStatementPayment_returnsPayment() throws Exception {
        when(paymentService.recordStatementPayment(eq(1L), any(Payment.class)))
                .thenReturn(testPayment);

        mockMvc.perform(post("/api/payments/statement/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testPayment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10000));
    }

    @Test
    void recordBillPayment_returnsPayment() throws Exception {
        when(paymentService.recordBillPayment(eq(1L), any(Payment.class)))
                .thenReturn(testPayment);

        mockMvc.perform(post("/api/payments/bill/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testPayment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10000));
    }
}
