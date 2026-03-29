package com.stopforfuel.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * IP-based rate limiting filter.
 * Allows 100 requests per minute per IP for general endpoints,
 * and 20 requests per minute for write operations (POST/PUT/DELETE).
 *
 * X-Forwarded-For is only trusted when running behind ALB (production).
 * IP format is validated to prevent spoofing via malformed headers.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::$|^(([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?::(([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?$");

    // AWS ALB private IP ranges (VPC CIDR)
    private static final Set<String> TRUSTED_PROXY_PREFIXES = Set.of(
            "10.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.",
            "172.25.", "172.26.", "172.27.", "172.28.", "172.29.",
            "172.30.", "172.31.", "192.168.", "127."
    );

    private static final int MAX_BUCKETS = 10_000;

    private final Map<String, Bucket> readBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> writeBuckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.trust-proxy:true}")
    private boolean trustProxy;

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

        // Evict buckets if too many to prevent memory exhaustion
        if (readBuckets.size() > MAX_BUCKETS) readBuckets.clear();
        if (writeBuckets.size() > MAX_BUCKETS) writeBuckets.clear();

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
        return Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();
    }

    private Bucket createWriteBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(20, Duration.ofMinutes(1)))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String remoteAddr = request.getRemoteAddr();
            // Only trust X-Forwarded-For if the direct connection is from a trusted proxy
            if (isTrustedProxy(remoteAddr)) {
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    String clientIp = xff.split(",")[0].trim();
                    if (isValidIp(clientIp)) {
                        return clientIp;
                    }
                }
            }
            return remoteAddr;
        }
        return request.getRemoteAddr();
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        for (String prefix : TRUSTED_PROXY_PREFIXES) {
            if (ip.startsWith(prefix)) return true;
        }
        // IPv6 loopback
        return "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) return false;
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }
}
