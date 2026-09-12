package com.stopforfuel.config;

import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.entity.AuditLog;
import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import com.stopforfuel.backend.repository.ApprovalRequestRepository;
import com.stopforfuel.backend.repository.AuditLogRepository;
import com.stopforfuel.backend.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The writes {@link PrivilegedActionAspect} makes on its own account, kept in a separate bean
 * so {@code REQUIRES_NEW} actually takes effect — a self-invoked {@code @Transactional} method
 * bypasses the proxy and would silently join the caller's transaction, which is exactly the
 * transaction being rolled back when a request is refused.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrivilegedActionRecorder {

    private final AuditLogRepository auditLogRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final PushNotificationService pushNotificationService;

    /** Audit rows must outlive the rollback of the request that produced them. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(Long actorId, String actorRole, String httpMethod, String path,
                      String actionKey, String outcome, String detail, String ipAddress, Long scid) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(actionFor(httpMethod));
            entry.setEntityType(resourceFrom(path));
            entry.setEntityId(idFrom(path));
            entry.setPerformedById(actorId);
            entry.setPerformedByName(actorRole);
            entry.setIpAddress(ipAddress);
            entry.setOutcome(outcome);
            entry.setDescription(outcome + " " + actionKey + (detail != null ? " — " + detail : ""));
            entry.setScid(scid);
            entry.setPerformedAt(LocalDateTime.now());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Auditing must never be the reason a request fails.
            log.warn("Audit write failed for {}: {}", actionKey, e.getMessage());
        }
    }

    /** Raise a blocked action for PRIME to review, surviving the caller's rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestApproval(Long actorId, String actorRole, String actionKey, long used) {
        try {
            // One pending request per action is enough — don't stack duplicates on retries.
            boolean alreadyPending = approvalRequestRepository
                    .findByRequestedByAndRequestTypeAndStatusOrderByCreatedAtDesc(
                            actorId, ApprovalRequestType.PRIVILEGED_ACTION, ApprovalRequestStatus.PENDING)
                    .stream()
                    .anyMatch(r -> r.getPayload() != null && r.getPayload().contains(jsonQuoted(actionKey)));
            if (alreadyPending) return;

            ApprovalRequest req = new ApprovalRequest();
            req.setRequestType(ApprovalRequestType.PRIVILEGED_ACTION);
            req.setStatus(ApprovalRequestStatus.PENDING);
            req.setRequestedBy(actorId);
            req.setPayload("{\"action\":" + jsonQuoted(actionKey)
                    + ",\"role\":" + jsonQuoted(actorRole)
                    + ",\"deletesUsed\":" + used + "}");
            req.setRequestNote("Delete limit reached — " + actorRole + " requested approval for " + actionKey);
            approvalRequestRepository.save(req);

            try {
                pushNotificationService.notifyApprovalRequestCreated(req, null, null);
            } catch (Exception e) {
                log.warn("Could not push privileged-action approval request: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Could not raise privileged-action approval request for {}: {}", actionKey, e.getMessage());
        }
    }

    /** Mark an approved grant as spent. Single-use: one approval buys one retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeGrant(Long grantId) {
        approvalRequestRepository.findById(grantId).ifPresent(grant -> {
            grant.setConsumedAt(LocalDateTime.now());
            approvalRequestRepository.save(grant);
        });
    }

    static String jsonQuoted(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private static String actionFor(String httpMethod) {
        return switch (httpMethod) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> httpMethod;
        };
    }

    /** "/api/invoice-bills/42/photos/7" -> "INVOICE-BILLS". */
    private static String resourceFrom(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("api".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1].toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    /** First numeric path segment, which is the target id for the conventional routes here. */
    private static Long idFrom(String path) {
        for (String part : path.split("/")) {
            if (!part.isEmpty() && part.chars().allMatch(Character::isDigit)) {
                try {
                    return Long.parseLong(part);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
