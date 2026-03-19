package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.BillSequence;
import com.stopforfuel.backend.repository.BillSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillSequenceServiceTest {

    @Mock
    private BillSequenceRepository billSequenceRepository;

    @InjectMocks
    private BillSequenceService billSequenceService;

    private int getCurrentFyYear() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        return (month >= 4 ? year : year - 1) % 100;
    }

    @Test
    void getNextBillNo_existingSequence_incrementsNumber() {
        int fyYear = getCurrentFyYear();
        BillSequence seq = new BillSequence();
        seq.setType("CASH");
        seq.setFyYear(fyYear);
        seq.setLastNumber(5L);

        when(billSequenceRepository.findByTypeAndFyYear("CASH", fyYear)).thenReturn(Optional.of(seq));
        when(billSequenceRepository.save(any(BillSequence.class))).thenAnswer(i -> i.getArgument(0));

        String result = billSequenceService.getNextBillNo("CASH");

        assertEquals("C" + fyYear + "/6", result);
        assertEquals(6L, seq.getLastNumber());
        verify(billSequenceRepository).save(seq);
    }

    @Test
    void getNextBillNo_newSequence_createsAndReturns1() {
        int fyYear = getCurrentFyYear();

        when(billSequenceRepository.findByTypeAndFyYear("CASH", fyYear)).thenReturn(Optional.empty());
        when(billSequenceRepository.save(any(BillSequence.class))).thenAnswer(i -> {
            BillSequence saved = i.getArgument(0);
            return saved;
        });

        String result = billSequenceService.getNextBillNo("CASH");

        assertEquals("C" + fyYear + "/1", result);
        verify(billSequenceRepository, times(2)).save(any(BillSequence.class));
    }

    @Test
    void getNextBillNo_cashType_usesCPrefix() {
        int fyYear = getCurrentFyYear();
        BillSequence seq = new BillSequence();
        seq.setType("CASH");
        seq.setFyYear(fyYear);
        seq.setLastNumber(0L);

        when(billSequenceRepository.findByTypeAndFyYear("CASH", fyYear)).thenReturn(Optional.of(seq));
        when(billSequenceRepository.save(any(BillSequence.class))).thenAnswer(i -> i.getArgument(0));

        String result = billSequenceService.getNextBillNo("CASH");

        assertTrue(result.startsWith("C"));
    }

    @Test
    void getNextBillNo_creditType_usesAPrefix() {
        int fyYear = getCurrentFyYear();
        BillSequence seq = new BillSequence();
        seq.setType("CREDIT");
        seq.setFyYear(fyYear);
        seq.setLastNumber(0L);

        when(billSequenceRepository.findByTypeAndFyYear("CREDIT", fyYear)).thenReturn(Optional.of(seq));
        when(billSequenceRepository.save(any(BillSequence.class))).thenAnswer(i -> i.getArgument(0));

        String result = billSequenceService.getNextBillNo("CREDIT");

        assertTrue(result.startsWith("A"));
        assertEquals("A" + fyYear + "/1", result);
    }
}
