package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.ReportAuditLog;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.ShiftClosingReport;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.enums.ShiftStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.ReportAuditLogRepository;
import com.stopforfuel.backend.repository.ShiftClosingReportRepository;
import com.stopforfuel.backend.repository.ShiftRepository;
import com.stopforfuel.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftCloseByCashierTest {

    @Mock private ShiftClosingReportRepository reportRepository;
    @Mock private ReportAuditLogRepository auditLogRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ShiftClosingReportService service;

    private Shift shift;
    private ShiftClosingReport report;

    @BeforeEach
    void setUp() {
        shift = new Shift();
        shift.setId(2700L);
        report = new ShiftClosingReport();
        report.setId(90L);
        report.setStatus("DRAFT");
        report.setShift(shift);

        User cashier = new User();
        cashier.setName("Rajendran M");
        when(userRepository.findById(315L)).thenReturn(Optional.of(cashier));
        when(reportRepository.findByIdAndScid(eq(90L), any())).thenReturn(Optional.of(report));
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditLogRepository.save(any(ReportAuditLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                Map.of("sub", "315", "custom:role", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void cashierClosesShiftInReviewAndIsRecordedAsCloser() {
        signInAs("CASHIER");
        shift.setStatus(ShiftStatus.REVIEW);

        ShiftClosingReport closed = service.finalizeReport(90L);

        assertEquals("FINALIZED", closed.getStatus());
        assertEquals("Rajendran M", closed.getFinalizedBy());
        assertEquals(ShiftStatus.RECONCILED, shift.getStatus());
    }

    @Test
    void cashierCannotFinalizeAnAdminCorrectionOnAClosedShift() {
        signInAs("CASHIER");
        shift.setStatus(ShiftStatus.CLOSED);

        assertThrows(BusinessException.class, () -> service.finalizeReport(90L));
        assertEquals("DRAFT", report.getStatus());
    }

    @Test
    void adminCanRefinalizeAClosedShiftAfterUnfinalize() {
        signInAs("ADMIN");
        shift.setStatus(ShiftStatus.CLOSED);

        assertEquals("FINALIZED", service.finalizeReport(90L).getStatus());
        assertEquals(ShiftStatus.RECONCILED, shift.getStatus());
    }

    @Test
    void nobodyClosesAShiftThatIsStillOpen() {
        signInAs("OWNER");
        shift.setStatus(ShiftStatus.OPEN);

        assertThrows(BusinessException.class, () -> service.finalizeReport(90L));
        verify(shiftRepository, never()).save(any());
    }
}
