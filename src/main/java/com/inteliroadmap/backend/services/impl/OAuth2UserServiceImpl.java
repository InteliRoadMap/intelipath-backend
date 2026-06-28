package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.internal.OAuth2UserInfoInternal;
import com.inteliroadmap.backend.domain.dto.internal.info.GitHubOauth2UserInfo;
import com.inteliroadmap.backend.domain.dto.internal.info.GoogleOAuth2UserInfo;
import com.inteliroadmap.backend.domain.entity.OauthAccount;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.OauthAccountRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.CustomOAuth2User;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.domain.entity.Student;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final StudentRepository studentRepository;

    /**
     * Loads user info from OAuth2 provider and creates or updates local user data.
     *
     * @param request the OAuth2 user request from Spring Security
     * @return CustomOAuth2User containing the authenticated user's email and role
     * @throws OAuth2AuthenticationException if OAuth2 authentication or user extraction fails
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        String provider = request.getClientRegistration().getRegistrationId();
        log.info("Loading OAuth2 user from provider: {}", provider);

        OAuth2User oauthUser = super.loadUser(request);
        Map<String, Object> attributes = resolveAttributes(provider, oauthUser, request);
        OAuth2UserInfoInternal userInfo = createUserInfo(provider, attributes);

        validateEmail(userInfo);

        User user = getOrCreateUser(userInfo);
        linkOauthAccountIfNeeded(user, userInfo, provider);

        log.info("OAuth2 login completed for email: {}, role: {}", user.getEmail(), user.getRole());

        return new CustomOAuth2User(oauthUser, user.getEmail(), user.getRole().name());
    }

    private Map<String, Object> resolveAttributes(
            String provider,
            OAuth2User oauthUser,
            OAuth2UserRequest request
    ) {
        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());

        if ("github".equalsIgnoreCase(provider) && attributes.get("email") == null) {
            fetchPrimaryGithubEmail(request.getAccessToken().getTokenValue())
                    .ifPresent(email -> attributes.put("email", email));
        }

        return attributes;
    }

    private Optional<String> fetchPrimaryGithubEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = RestClient.create("https://api.github.com")
                    .get()
                    .uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (emails == null) {
                return Optional.empty();
            }

            return emails.stream()
                    .filter(email -> Boolean.TRUE.equals(email.get("primary")))
                    .filter(email -> Boolean.TRUE.equals(email.get("verified")))
                    .map(email -> (String) email.get("email"))
                    .filter(email -> email != null && !email.isBlank())
                    .findFirst();
        } catch (Exception e) {
            log.warn("Could not fetch GitHub primary email: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Finds an existing user by email or creates a new student account.
     *
     * @param userInfo normalized OAuth2 user information
     * @return existing or newly created user
     */
    private User getOrCreateUser(OAuth2UserInfoInternal userInfo) {
        User user = userRepository.findByEmail(userInfo.getEmail());

        if (user == null) {
            log.info("No existing user found. Creating new OAuth2 user: {}", userInfo.getEmail());
            return createUser(userInfo);
        }

        log.info("Existing user found for OAuth2 login: {}", user.getEmail());
        return updateUserIfNeeded(user, userInfo);
    }

    /**
     * Creates a new local user from OAuth2 profile data.
     *
     * @param userInfo normalized OAuth2 user information
     * @return saved user entity
     */
    private User createUser(OAuth2UserInfoInternal userInfo) {
        User user = User.builder()
                .email(userInfo.getEmail())
                .fullName(userInfo.getFullName())
                .bio(userInfo.getBio())
                .role(UserRole.STUDENT)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Created new OAuth2 user with id: {}, email: {}", savedUser.getUserId(), savedUser.getEmail());

        Student student = Student.builder()
                .userId(savedUser.getUserId())
                .githubProfile(userInfo.getHtmlUrl())
                .portfolioSlug(com.inteliroadmap.backend.utils.SlugUtils.generateSlug(savedUser.getFullName(), savedUser.getUserId()))
                .build();
        studentRepository.save(student);

        return savedUser;
    }

    /**
     * Updates missing optional profile fields from OAuth2 data.
     *
     * @param user existing user entity
     * @param userInfo normalized OAuth2 user information
     * @return updated user if changes were made, otherwise original user
     */
    private User updateUserIfNeeded(User user, OAuth2UserInfoInternal userInfo) {
        boolean changed = false;

        if (user.getBio() == null && userInfo.getBio() != null) {
            user.setBio(userInfo.getBio());
            changed = true;
        }

        if (!changed) {
            log.debug("No OAuth2 profile update needed for user: {}", user.getEmail());
            return user;
        }

        User updatedUser = userRepository.save(user);
        log.info("Updated OAuth2 profile fields for user: {}", updatedUser.getEmail());

        return updatedUser;
    }

    /**
     * Links the OAuth2 provider account to the local user if it is not linked yet.
     *
     * @param user local user entity
     * @param userInfo normalized OAuth2 user information
     * @param provider OAuth2 provider name
     */
    private void linkOauthAccountIfNeeded(
            User user,
            OAuth2UserInfoInternal userInfo,
            String provider
    ) {
        boolean exists = oauthAccountRepository
                .findByProviderIdAndProviderName(userInfo.getProviderId(), provider)
                .isPresent();

        if (exists) {
            log.debug("OAuth2 account already linked. provider: {}, providerId: {}", provider, userInfo.getProviderId());
            return;
        }

        OauthAccount oauthAccount = OauthAccount.builder()
                .user(user)
                .providerId(userInfo.getProviderId())
                .providerName(provider)
                .build();

        oauthAccountRepository.save(oauthAccount);
        log.info("Linked OAuth2 account. user: {}, provider: {}, providerId: {}",
                user.getEmail(), provider, userInfo.getProviderId());
    }

    /**
     * Validates that OAuth2 provider returned a usable email.
     *
     * @param userInfo normalized OAuth2 user information
     * @throws OAuth2AuthenticationException if email is missing
     */
    private void validateEmail(OAuth2UserInfoInternal userInfo) {
        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            log.warn("OAuth2 provider did not return an email");
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }
    }

    /**
     * Creates provider-specific OAuth2 user info mapper.
     *
     * @param provider OAuth2 provider name
     * @param attributes raw OAuth2 attributes from provider
     * @return normalized OAuth2 user information
     */
    private OAuth2UserInfoInternal createUserInfo(
            String provider,
            Map<String, Object> attributes
    ) {
        log.debug("Creating OAuth2 user info mapper for provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "github" -> new GitHubOauth2UserInfo(attributes);
            default -> {
                log.warn("Unsupported OAuth2 provider: {}", provider);
                throw new ResourceNotFoundException("Unknown provider: " + provider);
            }
        };
    }
}
