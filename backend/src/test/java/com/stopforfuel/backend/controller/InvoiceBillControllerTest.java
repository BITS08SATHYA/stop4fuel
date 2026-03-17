package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.service.InvoiceBillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceBillController.class)
class InvoiceBillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InvoiceBillService service;

    private InvoiceBill testBill;

    @BeforeEach
    void setUp() {
        testBill = new InvoiceBill();
        testBill.setId(1L);
        testBill.setBillType("CASH");
        testBill.setNetAmount(new BigDecimal("5000"));
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(service.getAllInvoices()).thenReturn(List.of(testBill));

        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].billType").value("CASH"));
    }

    @Test
    void getByShift_returnsList() throws Exception {
        when(service.getInvoicesByShift(1L)).thenReturn(List.of(testBill));

        mockMvc.perform(get("/api/invoices/shift/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getById_returnsBill() throws Exception {
        when(service.getInvoiceById(1L)).thenReturn(testBill);

        mockMvc.perform(get("/api/invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billType").value("CASH"));
    }

    @Test
    void create_returnsCreatedBill() throws Exception {
        when(service.createInvoice(any(InvoiceBill.class))).thenReturn(testBill);

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testBill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billType").value("CASH"));
    }

    @Test
    void getByCustomer_returnsPage() throws Exception {
        when(service.getInvoicesByCustomer(eq(1L), isNull(), isNull(), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testBill)));

        mockMvc.perform(get("/api/invoices/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].billType").value("CASH"));
    }

    @Test
    void delete_returnsOk() throws Exception {
        doNothing().when(service).deleteInvoice(1L);

        mockMvc.perform(delete("/api/invoices/1"))
                .andExpect(status().isOk());

        verify(service).deleteInvoice(1L);
    }
}
