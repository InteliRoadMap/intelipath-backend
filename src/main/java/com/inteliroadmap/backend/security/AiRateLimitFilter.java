package com.inteliroadmap.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user throttle for the endpoints that spend OpenAI tokens (mentor chat and
 * the AI GitHub-import analysis). Keyed by the authenticated principal, not the
 * IP: OAuth login auto-provisions a STUDENT for any Google/GitHub user, so a
 * per-IP cap would be trivially bypassed with fresh accounts, and one user
 * behind NAT must not throttle another. Runs after JwtAuthenticationFilter so
 * the SecurityContext is populated.
 *
 * This is a cost guard, not a security control — the real backstop is the hard
 * monthly spend limit configured on the OpenAI account. Single-instance,
 * in-memory (fine for one VPS; a multi-instance deployment needs a shared store).
 */
@Component
@Slf4j
public class AiRateLimitFilter extends OncePerRequestFilter {

    @Value("${ai.rate-limit.max-requests:15}")
    private int maxRequests;

    @Value("${ai.rate-limit.window-seconds:3600}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    /** True for every request that must NOT be throttled. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !isAiEndpoint(request.getRequestURI());
    }

    private boolean isAiEndpoint(String uri) {
        // Mentor chat completion: /api/v1/chat/sessions/{sessionId}/stream
        if (uri.startsWith("/api/v1/chat/sessions/") && uri.endsWith("/stream")) {
            return true;
        }
        // AI GitHub-import analysis (single repo and the batch multiplier).
        return uri.equals("/api/v1/student/portfolio/projects/github-import")
                || uri.equals("/api/v1/student/portfolio/projects/github-import-batch")
                || uri.equals("/api/v1/student/portfolio/about/ai-draft");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userKey = currentUserKey();
        // Not authenticated: leave it to the security chain to reject (401). We
        // only throttle identified users.
        if (userKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long windowMillis = windowSeconds * 1000;
        long now = System.currentTimeMillis();

        Window window = windows.computeIfAbsent(userKey, k -> new Window(now));
        synchronized (window) {
            if (now - window.windowStart >= windowMillis) {
                window.windowStart = now;
                window.count.set(0);
            }
        }

        if (window.count.incrementAndGet() > maxRequests) {
            log.warn("AiRateLimitFilter: AI rate limit exceeded for user {} on {}", userKey, request.getRequestURI());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"You've reached the AI usage limit for now. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String currentUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    /** Drop windows for users who stopped calling so the map doesn't grow forever. */
    @Scheduled(fixedRate = 3_600_000)
    void evictExpiredWindows() {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000;
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= windowMillis * 2);
    }
}
