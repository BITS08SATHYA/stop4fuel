package com.stopforfuel.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.entity.DeviceToken;
import com.stopforfuel.backend.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreatePlatformEndpointRequest;
import software.amazon.awssdk.services.sns.model.EndpointDisabledException;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.NotFoundException;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final Optional<SnsClient> snsClient;
    private final ObjectMapper objectMapper;

    @Value("${stopforfuel.push.enabled:false}")
    private boolean enabled;

    @Value("${stopforfuel.push.sns-platform-app-arn:}")
    private String platformAppArn;

    /** Delete a device token (e.g. on logout) so we stop pushing to an ex-user's device. */
    @org.springframework.transaction.annotation.Transactional
    public void deleteToken(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        deviceTokenRepository.deleteByFcmToken(fcmToken);
    }

    /** Register (or refresh) a device token for the given user. */
    public DeviceToken registerToken(Long userId, String fcmToken, String platform) {
        DeviceToken token = deviceTokenRepository.findByFcmToken(fcmToken)
                .orElseGet(DeviceToken::new);
        token.setUserId(userId);
        token.setFcmToken(fcmToken);
        token.setPlatform(platform != null ? platform : "ANDROID");
        token.setLastSeenAt(LocalDateTime.now());

        if (enabled && snsClient.isPresent() && !platformAppArn.isBlank() && token.getSnsEndpointArn() == null) {
            try {
                var resp = snsClient.get().createPlatformEndpoint(CreatePlatformEndpointRequest.builder()
                        .platformApplicationArn(platformAppArn)
                        .token(fcmToken)
                        .build());
                token.setSnsEndpointArn(resp.endpointArn());
            } catch (Exception e) {
                log.warn("Failed to create SNS endpoint for user {}: {}", userId, e.getMessage());
            }
        }
        return deviceTokenRepository.save(token);
    }

    /** Fire a push notification for a newly submitted approval request. */
    public void notifyApprovalRequestCreated(ApprovalRequest req, String cashierName, String customerName) {
        if (!enabled || snsClient.isEmpty() || platformAppArn.isBlank()) {
            log.debug("Push disabled — skipping notify for approval #{}", req.getId());
            return;
        }
        List<DeviceToken> recipients = deviceTokenRepository.findByPermissionCode("APPROVAL_REQUEST_APPROVE");
        if (recipients.isEmpty()) return;

        String title = "New approval request";
        String body = String.format("%s requested %s%s",
                cashierName != null ? cashierName : "A cashier",
                req.getRequestType().name().replace("_", " ").toLowerCase(),
                customerName != null ? " for " + customerName : "");

        Map<String, Object> fcmNotif = Map.of("title", title, "body", body);
        Map<String, Object> fcmData = Map.of(
                "requestId", String.valueOf(req.getId()),
                "type", req.getRequestType().name()
        );
        Map<String, Object> gcmPayload = Map.of(
                "notification", fcmNotif,
                "data", fcmData
        );
        String snsMessage;
        try {
            snsMessage = objectMapper.writeValueAsString(Map.of(
                    "default", body,
                    "GCM", objectMapper.writeValueAsString(gcmPayload)
            ));
        } catch (Exception e) {
            log.warn("Failed to build push payload: {}", e.getMessage());
            return;
        }

        for (DeviceToken t : recipients) {
            if (t.getSnsEndpointArn() == null) continue;
            try {
                snsClient.get().publish(PublishRequest.builder()
                        .targetArn(t.getSnsEndpointArn())
                        .message(snsMessage)
                        .messageStructure("json")
                        .build());
            } catch (EndpointDisabledException | NotFoundException stale) {
                log.info("Stale device token (id={}), deleting", t.getId());
                deviceTokenRepository.delete(t);
            } catch (Exception e) {
                log.warn("Push to endpoint {} failed: {}", t.getSnsEndpointArn(), e.getMessage());
            }
        }
    }

    /**
     * Fire a push notification for a new direct message to a specific user.
     * Swallows all errors — messaging must never roll back because a device
     * is unreachable.
     */
    public void notifyMessageCreated(Long recipientUserId, String senderName, String text, Long conversationId) {
        if (!enabled || snsClient.isEmpty() || platformAppArn.isBlank()) {
            log.debug("Push disabled — skipping message notify to user {}", recipientUserId);
            return;
        }
        if (recipientUserId == null) return;
        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(recipientUserId);
        if (tokens.isEmpty()) return;

        String title = senderName != null && !senderName.isBlank() ? senderName : "New message";
        String body = text != null
                ? (text.length() > 200 ? text.substring(0, 200) + "…" : text)
                : "";

        Map<String, Object> fcmNotif = Map.of("title", title, "body", body);
        Map<String, Object> fcmData = Map.of(
                "type", "MESSAGE_CREATED",
                "conversationId", String.valueOf(conversationId != null ? conversationId : "")
        );
        Map<String, Object> gcmPayload = Map.of(
                "notification", fcmNotif,
                "data", fcmData
        );
        String snsMessage;
        try {
            snsMessage = objectMapper.writeValueAsString(Map.of(
                    "default", body,
                    "GCM", objectMapper.writeValueAsString(gcmPayload)
            ));
        } catch (Exception e) {
            log.warn("Failed to build message push payload: {}", e.getMessage());
            return;
        }

        for (DeviceToken t : tokens) {
            if (t.getSnsEndpointArn() == null) continue;
            try {
                snsClient.get().publish(PublishRequest.builder()
                        .targetArn(t.getSnsEndpointArn())
                        .message(snsMessage)
                        .messageStructure("json")
                        .build());
            } catch (EndpointDisabledException | NotFoundException stale) {
                log.info("Stale device token (id={}), deleting", t.getId());
                deviceTokenRepository.delete(t);
            } catch (Exception e) {
                log.warn("Message push to endpoint {} failed: {}", t.getSnsEndpointArn(), e.getMessage());
            }
        }
    }
}
