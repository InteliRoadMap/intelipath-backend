package com.inteliroadmap.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-IP rate limit for the credential-guessing-prone auth endpoints
 * (login, refresh, forgot/reset password). Single-instance, in-memory: fine for a
 * single-VPS deployment; a multi-instance deployment would need a shared store
 * (Redis) instead.
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    );

    @Value("${auth.rate-limit.max-requests:10}")
    private int maxRequests;

    @Value("${auth.rate-limit.window-seconds:60}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String key = clientIp(request) + ":" + request.getRequestURI();
        long windowMillis = windowSeconds * 1000;
        long now = System.currentTimeMillis();

        Window window = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (window) {
            if (now - window.windowStart >= windowMillis) {
                window.windowStart = now;
                window.count.set(0);
            }
        }

        if (window.count.incrementAndGet() > maxRequests) {
            log.warn("AuthRateLimitFilter: rate limit exceeded for {} on {}", clientIp(request), request.getRequestURI());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many attempts. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Cleared periodically so IPs that stop retrying don't linger in memory forever. */
    @Scheduled(fixedRate = 600_000)
    void evictExpiredWindows() {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000;
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= windowMillis * 2);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
