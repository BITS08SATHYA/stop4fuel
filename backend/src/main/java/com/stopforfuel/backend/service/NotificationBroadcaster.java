package com.stopforfuel.backend.service;

import com.stopforfuel.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory fan-out of server-sent events to currently connected users.
 * One user can have multiple live emitters (e.g. web tab + laptop).
 *
 * Events look like:
 *   event: approval
 *   data: {"type":"APPROVAL_REQUEST_CREATED","requestId":42, ...}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationBroadcaster {

    private final UserRepository userRepository;

    /** userId → active SSE emitters. */
    private final Map<Long, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /** Long-lived stream. SSE clients will auto-reconnect if the server closes them. */
    private static final long EMITTER_TIMEOUT_MS = 30L * 60_000L;

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Set<SseEmitter> bucket = emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        bucket.add(emitter);

        Runnable cleanup = () -> {
            Set<SseEmitter> b = emittersByUser.get(userId);
            if (b != null) {
                b.remove(emitter);
                if (b.isEmpty()) emittersByUser.remove(userId);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> { emitter.complete(); cleanup.run(); });
        emitter.onError(e -> { emitter.complete(); cleanup.run(); });

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("userId", userId)));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    /** Broadcast to every user whose role has the given permission code. */
    public void broadcastToPermission(String permissionCode, String eventName, Object payload) {
        List<Long> userIds = userRepository.findUserIdsByPermissionCode(permissionCode);
        for (Long userId : userIds) {
            sendToUser(userId, eventName, payload);
        }
    }

    /** Send to a single user across all their active connections. */
    public void sendToUser(Long userId, String eventName, Object payload) {
        Set<SseEmitter> bucket = emittersByUser.get(userId);
        if (bucket == null || bucket.isEmpty()) return;
        for (SseEmitter emitter : bucket) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                emitter.complete();
                bucket.remove(emitter);
            }
        }
    }

    /**
     * Keep connections alive through idle ALB timeouts (60s default).
     * Empty "comment" frames are a standard SSE keep-alive trick.
     */
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        for (Map.Entry<Long, Set<SseEmitter>> entry : emittersByUser.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (Exception e) {
                    emitter.complete();
                    entry.getValue().remove(emitter);
                }
            }
        }
    }
}
