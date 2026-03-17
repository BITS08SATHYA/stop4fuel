package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompanyService companyService;

    private Company testCompany;

    @BeforeEach
    void setUp() {
        testCompany = new Company();
        testCompany.setId(1L);
        testCompany.setName("Stop For Fuel");
    }

    @Test
    void getAllCompanies_returnsList() throws Exception {
        when(companyService.getAllCompanies()).thenReturn(List.of(testCompany));

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Stop For Fuel"));
    }

    @Test
    void getCompanyById_exists_returnsCompany() throws Exception {
        when(companyService.getCompanyById(1L)).thenReturn(Optional.of(testCompany));

        mockMvc.perform(get("/api/companies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stop For Fuel"));
    }

    @Test
    void getCompanyById_notExists_returnsNull() throws Exception {
        when(companyService.getCompanyById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/companies/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void createCompany_returnsCreatedCompany() throws Exception {
        when(companyService.saveCompany(any(Company.class))).thenReturn(testCompany);

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCompany)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stop For Fuel"));
    }

    @Test
    void updateCompany_returnsUpdatedCompany() throws Exception {
        testCompany.setName("Updated Name");
        when(companyService.saveCompany(any(Company.class))).thenReturn(testCompany);

        mockMvc.perform(put("/api/companies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCompany)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }
}
