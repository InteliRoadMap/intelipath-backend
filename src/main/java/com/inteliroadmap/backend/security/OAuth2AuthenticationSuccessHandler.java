package com.inteliroadmap.backend.security;

import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Handles successful OAuth2 authentication.
 * Generates JWT access and refresh tokens, saves the refresh token,
 * and redirects the user back to the configured frontend URI with the tokens.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${AUTHORIZED_REDIRECT_URI}")
    private String authorizedRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to {}", targetUrl);
            return;
        }

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * Determines the redirect URL by appending the generated tokens as query parameters.
     * @param request the HTTP request
     * @param response the HTTP response
     * @param authentication the authentication object containing CustomOAuth2User
     * @return the target URL for redirection
     */
    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        CustomOAuth2User oauth2User = (CustomOAuth2User) authentication.getPrincipal();
        String email = oauth2User.getEmail();
        String role = oauth2User.getRole();

        log.info("Generating tokens for OAuth2 user: {}", email);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(email, role);
        String refreshToken = jwtService.generateRefreshToken(email);

        // Save refresh token
        User user = userRepository.findByEmail(email);
        if (user != null) {
            RefreshToken token = RefreshToken.builder()
                    .token(refreshToken)
                    .user(user)
                    .expireAt(LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpiration())))
                    .build();
            refreshTokenRepository.save(token);
        }

        return UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();
    }
}
