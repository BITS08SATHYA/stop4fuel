package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.service.LedgerPdfGenerator;
import com.stopforfuel.backend.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = LedgerController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stopforfuel\\.config\\..*"))
class LedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LedgerService ledgerService;

    @MockBean
    private LedgerPdfGenerator ledgerPdfGenerator;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private CompanyRepository companyRepository;

    @Test
    void getOpeningBalance_returnsBalance() throws Exception {
        when(ledgerService.getOpeningBalance(eq(1L), any(LocalDate.class)))
                .thenReturn(new BigDecimal("15000"));

        mockMvc.perform(get("/api/ledger/opening-balance")
                        .param("customerId", "1")
                        .param("asOfDate", "2026-03-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(15000));
    }

    @Test
    void getCustomerLedger_returnsLedger() throws Exception {
        LedgerService.CustomerLedger ledger = new LedgerService.CustomerLedger();
        ledger.customerId = 1L;
        ledger.fromDate = LocalDate.of(2026, 3, 1);
        ledger.toDate = LocalDate.of(2026, 3, 31);
        ledger.openingBalance = new BigDecimal("5000");
        ledger.closingBalance = new BigDecimal("8000");
        ledger.totalDebits = new BigDecimal("5000");
        ledger.totalCredits = new BigDecimal("2000");
        ledger.entries = new ArrayList<>();

        when(ledgerService.getCustomerLedger(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(ledger);

        mockMvc.perform(get("/api/ledger/customer/1")
                        .param("fromDate", "2026-03-01")
                        .param("toDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(5000))
                .andExpect(jsonPath("$.closingBalance").value(8000));
    }

    @Test
    void getOutstandingBills_returnsList() throws Exception {
        InvoiceBill bill = new InvoiceBill();
        bill.setId(1L);
        bill.setNetAmount(new BigDecimal("3000"));
        when(ledgerService.getOutstandingBills(1L)).thenReturn(List.of(bill));

        mockMvc.perform(get("/api/ledger/outstanding/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }
}
