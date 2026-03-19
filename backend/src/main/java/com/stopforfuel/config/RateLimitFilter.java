package com.stopforfuel.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiting filter.
 * Allows 100 requests per minute per IP for general endpoints,
 * and 20 requests per minute for write operations (POST/PUT/DELETE).
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> readBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> writeBuckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        String method = httpRequest.getMethod();

        // Skip rate limiting for OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Apply stricter limit for write operations
        boolean isWrite = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);

        Bucket bucket;
        if (isWrite) {
            bucket = writeBuckets.computeIfAbsent(clientIp, k -> createWriteBucket());
        } else {
            bucket = readBuckets.computeIfAbsent(clientIp, k -> createReadBucket());
        }

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"timestamp\":\"" + java.time.LocalDateTime.now() + "\","
                    + "\"status\":429,"
                    + "\"error\":\"Too many requests. Please try again later.\"}");
        }
    }

    private Bucket createReadBucket() {
        // 100 requests per minute for reads
        return Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();
    }

    private Bucket createWriteBucket() {
        // 20 requests per minute for writes
        return Bucket.builder()
                .addLimit(Bandwidth.simple(20, Duration.ofMinutes(1)))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
