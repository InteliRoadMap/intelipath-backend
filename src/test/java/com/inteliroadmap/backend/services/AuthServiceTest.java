package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import com.inteliroadmap.backend.services.impl.AuthServiceImpl;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.security.TokenHashUtil;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtService = mock(JwtService.class);
        authService = new AuthServiceImpl(userRepository, refreshTokenRepository, jwtService);
    }

    @Test
    void refreshesTokensAndDeletesOldToken() {
        User user = user();
        RefreshToken storedToken = storedToken(user, LocalDateTime.now().plusHours(1));
        stubValidToken(storedToken, user);
        when(jwtService.generateAccessToken(user.getEmail(), user.getRole().name())).thenReturn("new-access");
        when(jwtService.generateRefreshToken(user.getEmail())).thenReturn("new-refresh");
        when(jwtService.getAccessExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshExpiration()).thenReturn(604800000L);

        RefreshResponse response = authService.refreshAccount("old-refresh");

        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
        assertNotNull(response.getExpiresIn());
        assertDoesNotThrow(() -> LocalDateTime.parse(response.getExpiresIn()));
        verify(refreshTokenRepository).delete(storedToken);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals(TokenHashUtil.sha256Hex("new-refresh"), tokenCaptor.getValue().getToken());
        assertEquals(user.getUserId(), tokenCaptor.getValue().getUser().getUserId());
    }

    @Test
    void rejectsExpiredJwt() {
        when(jwtService.isTokenValid("old-refresh")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> authService.refreshAccount("old-refresh"));

        verifyNoInteractions(userRepository);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidJwt() {
        when(jwtService.isTokenValid("old-refresh")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> authService.refreshAccount("old-refresh"));
    }

    @Test
    void rejectsRefreshTokenMissingFromDatabase() {
        when(jwtService.isTokenValid("old-refresh")).thenReturn(true);
        when(jwtService.extractEmail("old-refresh")).thenReturn("student@example.com");
        when(refreshTokenRepository.findByTokenForUpdate(TokenHashUtil.sha256Hex("old-refresh"))).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshAccount("old-refresh"));
    }

    @Test
    void rejectsMissingUser() {
        User user = user();
        RefreshToken storedToken = storedToken(user, LocalDateTime.now().plusHours(1));
        when(jwtService.isTokenValid("old-refresh")).thenReturn(true);
        when(jwtService.extractEmail("old-refresh")).thenReturn(user.getEmail());
        when(refreshTokenRepository.findByTokenForUpdate(TokenHashUtil.sha256Hex("old-refresh"))).thenReturn(Optional.of(storedToken));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> authService.refreshAccount("old-refresh"));
    }

    @Test
    void rejectsExpiredStoredToken() {
        User user = user();
        RefreshToken storedToken = storedToken(user, LocalDateTime.now().minusMinutes(1));
        when(jwtService.isTokenValid("old-refresh")).thenReturn(true);
        when(jwtService.extractEmail("old-refresh")).thenReturn(user.getEmail());
        when(refreshTokenRepository.findByTokenForUpdate(TokenHashUtil.sha256Hex("old-refresh"))).thenReturn(Optional.of(storedToken));

        assertThrows(ResponseStatusException.class, () -> authService.refreshAccount("old-refresh"));
    }

    private void stubValidToken(RefreshToken storedToken, User user) {
        when(jwtService.isTokenValid("old-refresh")).thenReturn(true);
        when(jwtService.extractEmail("old-refresh")).thenReturn(user.getEmail());
        when(refreshTokenRepository.findByTokenForUpdate(TokenHashUtil.sha256Hex("old-refresh"))).thenReturn(Optional.of(storedToken));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
    }

    private User user() {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("student@example.com")
                .role(UserRole.STUDENT)
                .build();
    }

    private RefreshToken storedToken(User user, LocalDateTime expiredAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("old-refresh")
                .user(user)
                .expiredAt(expiredAt)
                .build();
    }
}
