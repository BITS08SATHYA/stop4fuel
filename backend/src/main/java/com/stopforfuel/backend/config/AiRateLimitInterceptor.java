package com.stopforfuel.backend.config;

import com.stopforfuel.config.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Deque<Long>> userHits = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String key = resolveKey(req);
        long now = System.currentTimeMillis();
        Deque<Long> hits = userHits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() > WINDOW_MILLIS) {
                hits.pollFirst();
            }
            if (hits.size() >= MAX_REQUESTS_PER_WINDOW) {
                long retryAfterSec = Math.max(1, (WINDOW_MILLIS - (now - hits.peekFirst())) / 1000);
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setHeader("Retry-After", String.valueOf(retryAfterSec));
                res.setContentType("application/json");
                res.getWriter().write(
                        "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many AI requests. Try again in "
                                + retryAfterSec + "s.\"}");
                log.warn("AI rate limit exceeded for {} ({} req/min)", key, MAX_REQUESTS_PER_WINDOW);
                return false;
            }
            hits.addLast(now);
        }
        return true;
    }

    private String resolveKey(HttpServletRequest req) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid != null) {
            return "u:" + uid;
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return "ip:" + xff.split(",")[0].trim();
        }
        return "ip:" + req.getRemoteAddr();
    }
}
