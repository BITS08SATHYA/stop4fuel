package com.stopforfuel.config;

import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import com.stopforfuel.backend.exception.PrivilegeException;
import com.stopforfuel.backend.repository.ApprovalRequestRepository;
import com.stopforfuel.backend.repository.AuditLogRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The delete throttle exists because deletion here is permanent — no soft-delete column, no
 * recycle bin — so the damage one account can do in an afternoon is bounded by nothing else.
 */
class PrivilegedActionAspectTest {

    private static final int LIMIT = 5;

    private AuditLogRepository auditLogRepository;
    private ApprovalRequestRepository approvalRequestRepository;
    private PrivilegedActionRecorder recorder;
    private CurrentUserResolver currentUserResolver;
    private PrivilegedActionAspect aspect;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws Throwable {
        auditLogRepository = mock(AuditLogRepository.class);
        approvalRequestRepository = mock(ApprovalRequestRepository.class);
        recorder = mock(PrivilegedActionRecorder.class);
        currentUserResolver = mock(CurrentUserResolver.class);
        joinPoint = mock(ProceedingJoinPoint.class);

        when(joinPoint.proceed()).thenReturn("ok");
        when(currentUserResolver.currentUserId()).thenReturn(42L);
        when(approvalRequestRepository
                .findByRequestedByAndRequestTypeAndStatusAndConsumedAtIsNullOrderByReviewedAtDesc(
                        anyLong(), any(), any()))
                .thenReturn(List.of());

        aspect = new PrivilegedActionAspect(
                auditLogRepository, approvalRequestRepository, recorder, currentUserResolver);
        ReflectionTestUtils.setField(aspect, "deleteLimitPerDay", LIMIT);
        ReflectionTestUtils.setField(aspect, "grantValidityMinutes", 30);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // --- helpers ---------------------------------------------------------------------------

    private void request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr("10.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private void authenticatedAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        Map.of("sub", "42", "custom:role", role, "custom:scid", "1"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void deletesUsed(long count) {
        when(auditLogRepository.countByPerformedByIdAndActionAndOutcomeAndScidAndPerformedAtAfter(
                eq(42L), eq("DELETE"), eq("OK"), anyLong(), any(LocalDateTime.class)))
                .thenReturn(count);
    }

    // --- throttle ----------------------------------------------------------------------------

    @Test
    void deleteUnderTheLimitGoesThrough() throws Throwable {
        authenticatedAs("OWNER");
        request("DELETE", "/api/expense-types/7");
        deletesUsed(LIMIT - 1);

        assertEquals("ok", aspect.aroundMutation(joinPoint));
        verify(joinPoint).proceed();
        verify(recorder, never()).requestApproval(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void deleteAtTheLimitIsBlockedAndRaisedForApproval() throws Throwable {
        authenticatedAs("OWNER");
        request("DELETE", "/api/expense-types/7");
        deletesUsed(LIMIT);

        PrivilegeException ex = assertThrows(PrivilegeException.class,
                () -> aspect.aroundMutation(joinPoint));
        assertTrue(ex.getMessage().contains("Daily delete limit reached"), ex.getMessage());

        verify(joinPoint, never()).proceed();
        verify(recorder).requestApproval(42L, "OWNER", "DELETE /api/expense-types/7", LIMIT);
    }

    @Test
    void primeIsNotThrottled() throws Throwable {
        // PRIME has to be able to clean up after everyone else; throttling it would deadlock
        // with nobody able to lift the throttle.
        authenticatedAs("PRIME");
        request("DELETE", "/api/expense-types/7");
        deletesUsed(LIMIT * 10);

        assertEquals("ok", aspect.aroundMutation(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    void nonDeleteMutationsAreNeverThrottled() throws Throwable {
        authenticatedAs("OWNER");
        request("POST", "/api/expense-types");
        deletesUsed(LIMIT * 10);

        assertEquals("ok", aspect.aroundMutation(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    void anApprovedGrantLetsTheBlockedDeleteThrough() throws Throwable {
        authenticatedAs("OWNER");
        request("DELETE", "/api/expense-types/7");
        deletesUsed(LIMIT);

        ApprovalRequest grant = new ApprovalRequest();
        grant.setId(900L);
        grant.setRequestType(ApprovalRequestType.PRIVILEGED_ACTION);
        grant.setStatus(ApprovalRequestStatus.APPROVED);
        grant.setReviewedAt(LocalDateTime.now().minusMinutes(1));
        grant.setPayload("{\"action\":\"DELETE /api/expense-types/7\",\"role\":\"OWNER\"}");
        when(approvalRequestRepository
                .findByRequestedByAndRequestTypeAndStatusAndConsumedAtIsNullOrderByReviewedAtDesc(
                        eq(42L), eq(ApprovalRequestType.PRIVILEGED_ACTION), eq(ApprovalRequestStatus.APPROVED)))
                .thenReturn(List.of(grant));

        assertEquals("ok", aspect.aroundMutation(joinPoint));
        verify(joinPoint).proceed();
        // Single-use: one approval buys exactly one retry.
        verify(recorder).consumeGrant(900L);
    }

    @Test
    void aGrantForADifferentActionDoesNotUnlockThisOne() throws Throwable {
        authenticatedAs("OWNER");
        request("DELETE", "/api/customers/7");
        deletesUsed(LIMIT);

        ApprovalRequest grant = new ApprovalRequest();
        grant.setId(901L);
        grant.setStatus(ApprovalRequestStatus.APPROVED);
        grant.setReviewedAt(LocalDateTime.now().minusMinutes(1));
        grant.setPayload("{\"action\":\"DELETE /api/expense-types/7\"}");
        when(approvalRequestRepository
                .findByRequestedByAndRequestTypeAndStatusAndConsumedAtIsNullOrderByReviewedAtDesc(
                        anyLong(), any(), any()))
                .thenReturn(List.of(grant));

        assertThrows(PrivilegeException.class, () -> aspect.aroundMutation(joinPoint));
        verify(recorder, never()).consumeGrant(anyLong());
    }

    @Test
    void anExpiredGrantDoesNotUnlockTheDelete() throws Throwable {
        authenticatedAs("OWNER");
        request("DELETE", "/api/expense-types/7");
        deletesUsed(LIMIT);

        ApprovalRequest grant = new ApprovalRequest();
        grant.setId(902L);
        grant.setStatus(ApprovalRequestStatus.APPROVED);
        grant.setReviewedAt(LocalDateTime.now().minusHours(3));
        grant.setPayload("{\"action\":\"DELETE /api/expense-types/7\"}");
        when(approvalRequestRepository
                .findByRequestedByAndRequestTypeAndStatusAndConsumedAtIsNullOrderByReviewedAtDesc(
                        anyLong(), any(), any()))
                .thenReturn(List.of(grant));

        assertThrows(PrivilegeException.class, () -> aspect.aroundMutation(joinPoint));
        verify(recorder, never()).consumeGrant(anyLong());
    }

    // --- audit ---------------------------------------------------------------------------------

    @Test
    void successfulMutationsAreAudited() throws Throwable {
        authenticatedAs("OWNER");
        request("POST", "/api/invoices");

        aspect.aroundMutation(joinPoint);

        verify(recorder).audit(eq(42L), eq("OWNER"), eq("POST"), eq("/api/invoices"),
                eq("POST /api/invoices"), eq("OK"), isNull(), eq("10.0.0.9"), eq(1L));
    }

    @Test
    void refusedMutationsAreAuditedAndTheErrorStillPropagates() throws Throwable {
        authenticatedAs("OWNER");
        request("DELETE", "/api/customers/7");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("nope"));

        assertThrows(IllegalStateException.class, () -> aspect.aroundMutation(joinPoint));

        verify(recorder).audit(eq(42L), eq("OWNER"), eq("DELETE"), eq("/api/customers/7"),
                eq("DELETE /api/customers/7"), eq("FAILED"), eq("nope"), anyString(), eq(1L));
    }

    @Test
    void onlyCompletedDeletionsCountTowardTheBudget() throws Throwable {
        // A delete refused by a foreign key or a business rule destroyed nothing. If those were
        // charged to the budget, a few honest mistakes would lock the user out for the day.
        authenticatedAs("OWNER");
        request("DELETE", "/api/expense-types/7");
        deletesUsed(LIMIT - 1);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("in use"));

        assertThrows(IllegalStateException.class, () -> aspect.aroundMutation(joinPoint));

        // Recorded as FAILED, so the next call still sees LIMIT-1 successful deletes.
        verify(recorder).audit(anyLong(), anyString(), eq("DELETE"), anyString(), anyString(),
                eq("FAILED"), eq("in use"), anyString(), anyLong());
        verify(auditLogRepository, never())
                .countByPerformedByIdAndActionAndOutcomeAndScidAndPerformedAtAfter(
                        anyLong(), eq("DELETE"), eq("FAILED"), anyLong(), any(LocalDateTime.class));
    }

    @Test
    void anUnidentifiableActorIsNotThrottledButIsStillRecorded() throws Throwable {
        // Nothing to count deletes against, so the throttle cannot apply — but the attempt
        // must not vanish from the trail.
        authenticatedAs("OWNER");
        request("DELETE", "/api/expense-types/7");
        when(currentUserResolver.currentUserId()).thenReturn(null);
        deletesUsed(LIMIT);

        assertEquals("ok", aspect.aroundMutation(joinPoint));
        verify(recorder).audit(isNull(), eq("OWNER"), eq("DELETE"), anyString(), anyString(),
                eq("OK"), isNull(), anyString(), eq(1L));
    }
}
