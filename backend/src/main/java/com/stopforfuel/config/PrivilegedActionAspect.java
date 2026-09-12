package com.stopforfuel.config;

import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import com.stopforfuel.backend.exception.PrivilegeException;
import com.stopforfuel.backend.repository.ApprovalRequestRepository;
import com.stopforfuel.backend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Two controls on every state-changing API call, in one place because they share the same
 * bookkeeping.
 *
 * <p><b>Audit trail.</b> Before this existed, {@code AuditLogService} was called from exactly
 * two places — login and shift-report finalisation — so no mutation in the system recorded who
 * performed it. Every non-GET controller method now writes a row, whether it succeeded or was
 * refused.
 *
 * <p><b>Delete throttle.</b> Deletions are hard in this codebase (no soft-delete column, no
 * recycle bin), so a single account can quietly erase a lot of history. Any role below PRIME
 * gets {@code app.security.delete-limit-per-day} deletes in a rolling 24 hours; past that the
 * attempt becomes a PENDING {@link ApprovalRequestType#PRIVILEGED_ACTION} that notifies PRIME.
 * Approving it does not replay the delete — it issues a single-use grant, and the requester
 * retries it themselves, so the audit row names the real actor rather than the approver.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
// Must wrap outside Spring Security's method interceptor. Inside it, a @PreAuthorize denial
// throws before this advice ever runs, so the attempts most worth recording — someone probing
// a PRIME-only endpoint — would leave no audit row at all.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrivilegedActionAspect {

    private final AuditLogRepository auditLogRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final PrivilegedActionRecorder recorder;
    private final CurrentUserResolver currentUserResolver;

    @Value("${app.security.delete-limit-per-day:5}")
    private int deleteLimitPerDay;

    /** How long an approved grant stays spendable before the requester must ask again. */
    @Value("${app.security.grant-validity-minutes:30}")
    private int grantValidityMinutes;

    @Around("within(com.stopforfuel.backend.controller..*) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PatchMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object aroundMutation(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        String httpMethod = request != null ? request.getMethod() : "UNKNOWN";
        String path = request != null ? request.getRequestURI() : pjp.getSignature().toShortString();
        String actionKey = httpMethod + " " + path;

        Long actorId = currentUserResolver.currentUserId();
        String actorRole = SecurityUtils.getCurrentRole();
        Long scid = SecurityUtils.getScid();
        String ip = clientIp(request);

        if ("DELETE".equals(httpMethod)) {
            enforceDeleteLimit(actorId, actorRole, actionKey, scid, ip);
        }

        try {
            Object result = pjp.proceed();
            recorder.audit(actorId, actorRole, httpMethod, path, actionKey, "OK", null, ip, scid);
            return result;
        } catch (Throwable ex) {
            // Refusals are the rows you most want when reconstructing what someone tried.
            recorder.audit(actorId, actorRole, httpMethod, path, actionKey, "FAILED", ex.getMessage(), ip, scid);
            throw ex;
        }
    }

    private void enforceDeleteLimit(Long actorId, String actorRole, String actionKey, Long scid, String ip) {
        // PRIME is the tier that has to be able to clean up after everyone else; throttling it
        // would just create a deadlock with nobody able to lift the throttle.
        if (RoleHierarchy.isPrime(actorRole)) return;
        if (actorId == null) return;
        if (deleteLimitPerDay <= 0) return;

        LocalDateTime since = LocalDateTime.now().minusDays(1);
        // Only completed deletions count. An attempt refused by a foreign key, a business rule
        // or an authorization check removed nothing, so charging it to the budget would punish
        // someone for a mistake and let a handful of typos lock them out for the day.
        long used = auditLogRepository.countByPerformedByIdAndActionAndOutcomeAndScidAndPerformedAtAfter(
                actorId, "DELETE", "OK", scid, since);

        if (used < deleteLimitPerDay) return;

        if (spendGrant(actorId, actionKey)) {
            log.info("Delete limit: user {} spent an approved grant for {}", actorId, actionKey);
            return;
        }

        recorder.requestApproval(actorId, actorRole, actionKey, used);
        recorder.audit(actorId, actorRole, "DELETE", actionKey.substring(actionKey.indexOf(' ') + 1),
                actionKey, "BLOCKED", "daily delete limit reached; sent to PRIME for approval", ip, scid);

        throw new PrivilegeException(
                "Daily delete limit reached (" + used + " of " + deleteLimitPerDay + " in the last 24 hours). "
                        + "This request has been sent to PRIME for approval — once approved you can retry it.");
    }

    /**
     * Consume an approved grant matching this exact action, if one is unspent and still inside
     * its validity window. Single-use by design: one approval, one action.
     */
    private boolean spendGrant(Long actorId, String actionKey) {
        List<ApprovalRequest> grants = approvalRequestRepository
                .findByRequestedByAndRequestTypeAndStatusAndConsumedAtIsNullOrderByReviewedAtDesc(
                        actorId, ApprovalRequestType.PRIVILEGED_ACTION, ApprovalRequestStatus.APPROVED);

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(grantValidityMinutes);
        for (ApprovalRequest grant : grants) {
            if (grant.getReviewedAt() == null || grant.getReviewedAt().isBefore(cutoff)) continue;
            if (grant.getPayload() == null
                    || !grant.getPayload().contains(PrivilegedActionRecorder.jsonQuoted(actionKey))) {
                continue;
            }
            recorder.consumeGrant(grant.getId());
            return true;
        }
        return false;
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
