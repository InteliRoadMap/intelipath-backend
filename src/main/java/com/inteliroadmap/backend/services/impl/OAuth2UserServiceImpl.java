package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.internal.OAuth2UserInfoInternal;
import com.inteliroadmap.backend.domain.dto.internal.info.GitHubOauth2UserInfo;
import com.inteliroadmap.backend.domain.dto.internal.info.GoogleOAuth2UserInfo;
import com.inteliroadmap.backend.domain.entity.OauthAccount;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.AccountType;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.repositories.OauthAccountRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.CustomOAuth2User;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.services.PortfolioSlugService;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * Custom implementation of {@link DefaultOAuth2UserService} to handle OAuth2 login flows.
 * This service intercepts the OAuth2 authentication process, retrieves user information
 * from the provider (like Google or GitHub), normalizes it, and manages the local user
 * and student records, including linking OAuth2 accounts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final StudentRepository studentRepository;
    private final PortfolioSlugService portfolioSlugService;

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
        // Retrieve the provider (e.g., github, google) from the OAuth2 client registration
        String provider = request.getClientRegistration().getRegistrationId();
        log.info("OAuth2UserServiceImpl: Loading OAuth2 user from provider: {}", provider);

        // Delegate to DefaultOAuth2UserService to fetch the basic user info
        OAuth2User oauthUser = super.loadUser(request);
        // Extract and normalize the attributes (handling specific cases like missing GitHub email)
        Map<String, Object> attributes = resolveAttributes(provider, oauthUser, request);
        // Create an internal DTO that maps attributes uniformly regardless of provider
        OAuth2UserInfoInternal userInfo = createUserInfo(provider, attributes);

        // Ensure the email is present and valid
        validateEmail(userInfo);

        // Retrieve existing local user or create a new one using the OAuth2 details
        User user = getOrCreateUser(userInfo);
        // Link the OAuth account to the user record if not already linked
        linkOauthAccountIfNeeded(user, userInfo, provider);

        log.info("OAuth2UserServiceImpl: OAuth2 login completed for email: {}, role: {}", user.getEmail(), user.getRole());

        // Return a custom user principal holding the email and role for Spring Security
        return new CustomOAuth2User(oauthUser, user.getEmail(), user.getRole().name());
    }

    /**
     * Resolves and normalizes attributes from the OAuth2 provider.
     * Special handling is included for GitHub to fetch the primary email if it is hidden.
     *
     * @param provider The OAuth2 provider name (e.g., github, google)
     * @param oauthUser The authenticated OAuth2 user
     * @param request The OAuth2 user request containing the access token
     * @return A map of resolved user attributes
     */
    private Map<String, Object> resolveAttributes(
            String provider,
            OAuth2User oauthUser,
            OAuth2UserRequest request
    ) {
        // Initialize a mutable map with the default attributes
        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());

        // For GitHub, the email might be private and excluded from default attributes
        // In this case, we explicitly fetch it from GitHub's email API
        if ("github".equalsIgnoreCase(provider) && attributes.get("email") == null) {
            fetchPrimaryGithubEmail(request.getAccessToken().getTokenValue())
                    .ifPresent(email -> attributes.put("email", email));
        }

        return attributes;
    }

    /**
     * Fetches the primary, verified email address from the GitHub API using the user's access token.
     *
     * @param accessToken The GitHub OAuth2 access token
     * @return An {@link Optional} containing the primary email address, or empty if not found or on error
     */
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
            log.warn("OAuth2UserServiceImpl: Could not fetch GitHub primary email: {}", e.getMessage());
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
        // Attempt to find a user matching the provided email
        User user = userRepository.findByEmail(userInfo.getEmail());

        if (user == null) {
            // If the user doesn't exist, proceed to create a new one
            log.info("OAuth2UserServiceImpl: No existing user found. Creating new OAuth2 user: {}", userInfo.getEmail());
            return createUser(userInfo);
        }

        // If the user exists, log the discovery and potentially update missing fields
        log.info("OAuth2UserServiceImpl: Existing user found for OAuth2 login: {}", user.getEmail());
        return updateUserIfNeeded(user, userInfo);
    }

    /**
     * Creates a new local user from OAuth2 profile data.
     *
     * @param userInfo normalized OAuth2 user information
     * @return saved user entity
     */
    private User createUser(OAuth2UserInfoInternal userInfo) {
        // Build a new User entity using information retrieved from the OAuth2 provider
        User user = User.builder()
                .email(userInfo.getEmail())
                .fullName(userInfo.getFullName())
                .bio(userInfo.getBio())
                .role(UserRole.STUDENT) // Defaulting all new OAuth users to STUDENT role
                // Arriving through OAuth means this is not a counselor-provisioned FPT
                // account, so no FPT material. Existing users keep whatever type they have.
                .accountType(AccountType.OTHER)
                .build();

        // Save the new User entity to the database
        User savedUser = userRepository.save(user);
        log.info("OAuth2UserServiceImpl: Created new OAuth2 user with id: {}, email: {}", savedUser.getUserId(), savedUser.getEmail());

        // Automatically create a corresponding Student profile associated with the new User
        Student student = Student.builder()
                .userId(savedUser.getUserId())
                .githubProfile(userInfo.getHtmlUrl())
                .portfolioSlug(portfolioSlugService.allocateFor(savedUser))
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
            log.debug("OAuth2UserServiceImpl: No OAuth2 profile update needed for user: {}", user.getEmail());
            return user;
        }

        User updatedUser = userRepository.save(user);
        log.info("OAuth2UserServiceImpl: Updated OAuth2 profile fields for user: {}", updatedUser.getEmail());

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
        // Check if an OAuth link for this provider and user already exists
        boolean exists = oauthAccountRepository
                .findByProviderIdAndProviderName(userInfo.getProviderId(), provider)
                .isPresent();

        if (exists) {
            // Already linked, no action needed
            log.debug("OAuth2UserServiceImpl: OAuth2 account already linked. provider: {}, providerId: {}", provider, userInfo.getProviderId());
            return;
        }

        // Create a new OauthAccount record to link the external provider ID to the local user
        OauthAccount oauthAccount = OauthAccount.builder()
                .user(User.builder().userId(user.getUserId()).build())
                .providerId(userInfo.getProviderId())
                .providerName(provider)
                .build();

        // Persist the mapping
        oauthAccountRepository.save(oauthAccount);
        log.info("OAuth2UserServiceImpl: Linked OAuth2 account. user: {}, provider: {}, providerId: {}",
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
            log.warn("OAuth2UserServiceImpl: OAuth2 provider did not return an email");
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
        log.debug("OAuth2UserServiceImpl: Creating OAuth2 user info mapper for provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "github" -> new GitHubOauth2UserInfo(attributes);
            default -> {
                log.warn("OAuth2UserServiceImpl: Unsupported OAuth2 provider: {}", provider);
                throw new BadRequestException("Unknown provider: " + provider);
            }
        };
    }
}
