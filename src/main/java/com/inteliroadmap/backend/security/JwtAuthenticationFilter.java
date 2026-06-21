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
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {


    private final JwtService jwtService;
    private final AuthenticationCookieService authenticationCookieService;

    /**
     * Refresh requests validate their token in the authentication service.
     *
     * @param request current HTTP request
     * @return true when this filter must not process the request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/auth/refresh".equals(request.getRequestURI());
    }

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

            String token = resolveToken(request);
            if (token == null) {
                log.debug("No JWT token found in Authorization header or access cookie");
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("JWT token extracted from request");

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

            String role  = jwtService.extractRole(token);
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

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return authenticationCookieService.getAccessToken(request).orElse(null);
    }
}
