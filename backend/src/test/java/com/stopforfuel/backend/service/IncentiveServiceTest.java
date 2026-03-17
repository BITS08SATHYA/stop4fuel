package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Incentive;
import com.stopforfuel.backend.repository.IncentiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncentiveServiceTest {

    @Mock
    private IncentiveRepository repository;

    @InjectMocks
    private IncentiveService incentiveService;

    private Incentive testIncentive;

    @BeforeEach
    void setUp() {
        testIncentive = new Incentive();
        testIncentive.setId(1L);
        testIncentive.setMinQuantity(new BigDecimal("100"));
        testIncentive.setDiscountRate(new BigDecimal("0.50"));
        testIncentive.setActive(true);
        testIncentive.setScid(1L);
    }

    @Test
    void getByCustomer_returnsList() {
        when(repository.findByCustomerId(1L)).thenReturn(List.of(testIncentive));
        assertEquals(1, incentiveService.getByCustomer(1L).size());
    }

    @Test
    void getActiveIncentive_returnsOptional() {
        when(repository.findByCustomerIdAndProductIdAndActiveTrue(1L, 1L))
                .thenReturn(Optional.of(testIncentive));
        assertTrue(incentiveService.getActiveIncentive(1L, 1L).isPresent());
    }

    @Test
    void create_setsDefaultScid() {
        Incentive i = new Incentive();
        i.setDiscountRate(new BigDecimal("1.00"));
        when(repository.save(any(Incentive.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(1L, incentiveService.create(i).getScid());
    }

    @Test
    void update_updatesFields() {
        Incentive details = new Incentive();
        details.setMinQuantity(new BigDecimal("200"));
        details.setDiscountRate(new BigDecimal("0.75"));
        details.setActive(false);

        when(repository.findById(1L)).thenReturn(Optional.of(testIncentive));
        when(repository.save(any(Incentive.class))).thenAnswer(i -> i.getArgument(0));

        Incentive result = incentiveService.update(1L, details);
        assertEquals(new BigDecimal("200"), result.getMinQuantity());
        assertEquals(new BigDecimal("0.75"), result.getDiscountRate());
        assertFalse(result.isActive());
    }

    @Test
    void update_notFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> incentiveService.update(99L, new Incentive()));
    }

    @Test
    void deactivate_setsActiveFalse() {
        when(repository.findById(1L)).thenReturn(Optional.of(testIncentive));

        incentiveService.deactivate(1L);

        assertFalse(testIncentive.isActive());
        verify(repository).save(testIncentive);
    }

    @Test
    void deactivate_notFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> incentiveService.deactivate(99L));
    }
}
