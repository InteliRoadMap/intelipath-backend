package com.inteliroadmap.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${FRONTEND_API_KEY:baf8098d43a741c183ff56cf9cbec51f}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        System.out.println(">>> ApiKeyFilter HIT for URI: " + request.getRequestURI());

        // Only apply API key check to the register endpoint
        if ("/api/v1/auth/register".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
            String requestApiKey = request.getHeader("x-api-key");
            System.out.println(">>> ApiKeyFilter: Found x-api-key = " + requestApiKey);

            if (apiKey == null || !apiKey.equals(requestApiKey)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"message\": \"Invalid API Key\"}");
                return;
            }
        }

        // Proceed to the next filter
        filterChain.doFilter(request, response);
    }
}
