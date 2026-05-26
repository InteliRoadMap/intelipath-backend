package com.inteliroadmap.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {


    private final JwtService jwtService;

     /**
     * Filter logic - Chạy trên mỗi HTTP request
     *
     * @param request     HTTP request
     * @param response    HTTP response
     * @param filterChain Filter chain
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            log.debug("Processing request: {} {}", request.getMethod(), request.getRequestURI());

            // Extract header from token
            String authHeader = request.getHeader("Authorization");

            // Check header is null or not starts with "Bearer" scheme
            // May be don't need authentication: Public endpoint, or client not send token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("No JWT token found in Authorization header");
                filterChain.doFilter(request, response);
                return;
            }

            // Extract token from Header "Bearer "
            String token = authHeader.substring(7);
            log.debug("JWT token extracted from header");

            // Check token is valid
            // Ex: header.payload.signature, token expired?, JWT format token is valid?,...
            if (!jwtService.isTokenValid(token)) {
                log.warn("JWT token validation failed");
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("JWT token is valid");

            //Get Email from token, if it cannot extract email (ex. token is valid not have subject), btw also bypass this filter or can response error 401 Unauthorized
            String email = jwtService.extractEmail(token);
            if (email == null) {
                log.warn("Failed to extract email from token");
                filterChain.doFilter(request, response);
                return;
            }
            log.debug("Email extracted: {}", email);

            String role  = jwtService.extractRole(email);
            if (role == null) {
                log.warn("Failed to extract role from token");
                filterChain.doFilter(request, response);
                return;
            }
            log.debug("Role extracted: {}", role);

            List<GrantedAuthority> authorityList
                    = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            UsernamePasswordAuthenticationToken authentication
                    = new UsernamePasswordAuthenticationToken(email, null, authorityList);


            // Gửi authentication vào SecurityContext để Spring Security biết user đã authenticated
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("Authentication set for user: {}", email);

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Error in JWT authentication filter: {}", e.getMessage());
            filterChain.doFilter(request, response);
        }
    }
}