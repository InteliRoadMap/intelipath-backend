package com.inteliroadmap.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.AccountType;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Granted to FPT accounts; gates the FPT curriculum and its material. */
    public static final String ACCOUNT_FPT_AUTHORITY = "ACCOUNT_FPT";

    private final JwtService jwtService;
    private final UserRepository userRepository;

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
            log.debug("JwtAuthenticationFilter: Processing request: {} {}", request.getMethod(), request.getRequestURI());

            // Extract header from token
            String authHeader = request.getHeader("Authorization");

            // Check header is null or not starts with "Bearer" scheme
            // May be don't need authentication: Public endpoint, or client not send token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("JwtAuthenticationFilter: No JWT token found in Authorization header");
                filterChain.doFilter(request, response);
                return;
            }

            // Extract token from Header "Bearer "
            String token = authHeader.substring(7);
            log.debug("JwtAuthenticationFilter: JWT token extracted from header");

            // Check token is valid
            // Ex: header.payload.signature, token expired?, JWT format token is valid?,...
            if (!jwtService.isTokenValid(token)) {
                log.warn("JwtAuthenticationFilter: JWT token validation failed");
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("JwtAuthenticationFilter: JWT token is valid");

            //Get Email from token, if it cannot extract email (ex. token is valid not have subject), btw also bypass this filter or can response error 401 Unauthorized
            String email = jwtService.extractEmail(token);
            if (email == null) {
                log.warn("JwtAuthenticationFilter: Failed to extract email from token");
                filterChain.doFilter(request, response);
                return;
            }
            log.debug("JwtAuthenticationFilter: Email extracted: {}", email);

            String role  = jwtService.extractRole(token);
            if (role == null) {
                log.warn("JwtAuthenticationFilter: Failed to extract role from token");
                filterChain.doFilter(request, response);
                return;
            }
            log.debug("JwtAuthenticationFilter: Role extracted: {}", role);

            // Suspended / deactivated accounts keep valid tokens but must be blocked
            // from every authenticated endpoint until an admin reactivates them.
            User user = userRepository.findByEmail(email);
            if (user != null && user.getUserStatus() != null && user.getUserStatus() != UserStatus.ACTIVE) {
                log.warn("JwtAuthenticationFilter: Rejecting request from {} account: {}", user.getUserStatus(), email);
                filterChain.doFilter(request, response);
                return;
            }

            List<GrantedAuthority> authorityList = new ArrayList<>();
            authorityList.add(new SimpleGrantedAuthority("ROLE_" + role));
            // Account type is read from the User row rather than a JWT claim: the row is
            // already loaded above, and a claim would stay stale until the access token
            // expires. Endpoints gate FPT material with hasAuthority('ACCOUNT_FPT').
            if (user != null && user.getAccountType() == AccountType.FPT) {
                authorityList.add(new SimpleGrantedAuthority(ACCOUNT_FPT_AUTHORITY));
            }
            UsernamePasswordAuthenticationToken authentication
                    = new UsernamePasswordAuthenticationToken(email, null, authorityList);

            // Gửi authentication vào SecurityContext để Spring Security biết user đã authenticated
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("JwtAuthenticationFilter: Authentication set for user: {}", email);

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("JwtAuthenticationFilter: Error in JWT authentication filter: {}", e.getMessage());
            filterChain.doFilter(request, response);
        }
    }
}
