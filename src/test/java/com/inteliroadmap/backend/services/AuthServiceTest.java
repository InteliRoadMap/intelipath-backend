package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import com.inteliroadmap.backend.services.impl.AuthServiceImpl;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.AccountType;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.security.TokenHashUtil;
import com.inteliroadmap.backend.repositories.PasswordResetTokenRepository;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        jwtService = mock(JwtService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        authService = new AuthServiceImpl(userRepository, refreshTokenRepository, passwordResetTokenRepository,
                jwtService, passwordEncoder, emailService);
    }

    @Test
    void refreshesTokensAndLeavesOldTokenInGraceWindow() {
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
        // Must parse as an instant, not a zone-less local time: a client in another timezone
        // reads a zone-less expiry as its own local clock and refreshes on a hot loop.
        assertDoesNotThrow(() -> Instant.parse(response.getExpiresIn()));
        assertTrue(response.getExpiresIn().endsWith("Z"));

        // The used token is kept alive briefly rather than deleted, so a concurrent
        // refresh carrying the same cookie still resolves.
        verify(refreshTokenRepository, never()).delete(any());
        assertTrue(storedToken.getExpiredAt().isBefore(LocalDateTime.now().plusMinutes(1)));

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(tokenCaptor.capture());
        RefreshToken newToken = tokenCaptor.getAllValues().get(1);
        assertEquals(TokenHashUtil.sha256Hex("new-refresh"), newToken.getToken());
        assertEquals(user.getUserId(), newToken.getUser().getUserId());
    }

    @Test
    void logsInWithValidCredentials() {
        User user = fptUser();
        when(userRepository.findByUsername("hau.st")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user.getEmail(), user.getRole().name())).thenReturn("access");
        when(jwtService.generateRefreshToken(user.getEmail())).thenReturn("refresh");
        when(jwtService.getAccessExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshExpiration()).thenReturn(604800000L);

        RefreshResponse response = authService.login(loginRequest("hau.st", "secret"));

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());

        // The refresh token is persisted hashed, never in the clear.
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals(TokenHashUtil.sha256Hex("refresh"), tokenCaptor.getValue().getToken());
    }

    @Test
    void rejectsWrongPassword() {
        User user = fptUser();
        when(userRepository.findByUsername("hau.st")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> authService.login(loginRequest("hau.st", "wrong")));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownUsernameWithSameErrorAsWrongPassword() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException unknown = assertThrows(ResponseStatusException.class,
                () -> authService.login(loginRequest("ghost", "secret")));

        User user = fptUser();
        when(userRepository.findByUsername("hau.st")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);
        ResponseStatusException wrongPassword = assertThrows(ResponseStatusException.class,
                () -> authService.login(loginRequest("hau.st", "wrong")));

        // Identical status and message, so neither reveals which accounts exist.
        assertEquals(wrongPassword.getStatusCode(), unknown.getStatusCode());
        assertEquals(wrongPassword.getReason(), unknown.getReason());
    }

    @Test
    void rejectsOauthAccountWithoutPassword() {
        User oauthUser = user(); // no passwordHash: arrived through GitHub
        when(userRepository.findByUsername("oauth.user")).thenReturn(Optional.of(oauthUser));

        assertThrows(ResponseStatusException.class, () -> authService.login(loginRequest("oauth.user", "secret")));
        // Never reaches the encoder: a null hash is rejected outright.
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rejectsSuspendedAccount() {
        User user = fptUser();
        user.setUserStatus(UserStatus.SUSPENDED);
        when(userRepository.findByUsername("hau.st")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> authService.login(loginRequest("hau.st", "secret")));

        assertEquals(HttpStatus.FORBIDDEN, thrown.getStatusCode());
        verify(refreshTokenRepository, never()).save(any());
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

    /** A counselor-provisioned FPT account: has a username and a local credential. */
    private User fptUser() {
        User user = user();
        user.setUsername("hau.st");
        user.setPasswordHash("hashed");
        user.setAccountType(AccountType.FPT);
        user.setUserStatus(UserStatus.ACTIVE);
        return user;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
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
