package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.RefreshRequest;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.services.AuthService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthService {

    public RefreshResponse refreshAccount(RefreshRequest refreshRequest) ;

    public RefreshResponse refreshResponse(String accessToken, String refreshToken, LocalDateTime expiresIn) ;

    public ResponseStatusException invalidRefreshToken() ;
}
