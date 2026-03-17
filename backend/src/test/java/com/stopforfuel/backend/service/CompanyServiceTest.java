package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyService companyService;

    private Company testCompany;

    @BeforeEach
    void setUp() {
        testCompany = new Company();
        testCompany.setId(1L);
        testCompany.setName("Stop For Fuel");
        testCompany.setScid(1L);
    }

    @Test
    void getAllCompanies_returnsList() {
        when(companyRepository.findAll()).thenReturn(List.of(testCompany));
        assertEquals(1, companyService.getAllCompanies().size());
    }

    @Test
    void getCompaniesByScid_returnsList() {
        when(companyRepository.findByScid(1L)).thenReturn(List.of(testCompany));
        assertEquals(1, companyService.getCompaniesByScid(1L).size());
    }

    @Test
    void getCompanyById_exists_returnsOptional() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        assertTrue(companyService.getCompanyById(1L).isPresent());
    }

    @Test
    void getCompanyById_notExists_returnsEmpty() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());
        assertTrue(companyService.getCompanyById(99L).isEmpty());
    }

    @Test
    void saveCompany_savesAndReturns() {
        when(companyRepository.save(testCompany)).thenReturn(testCompany);
        assertEquals("Stop For Fuel", companyService.saveCompany(testCompany).getName());
    }

    @Test
    void deleteCompany_callsRepository() {
        companyService.deleteCompany(1L);
        verify(companyRepository).deleteById(1L);
    }
}
