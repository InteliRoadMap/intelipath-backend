package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.internal.OAuth2UserInfoInternal;
import com.inteliroadmap.backend.domain.dto.internal.info.GitHubOauth2UserInfo;
import com.inteliroadmap.backend.domain.dto.internal.info.GoogleOAuth2UserInfo;
import com.inteliroadmap.backend.domain.entity.OauthAccount;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.OauthAccountRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.security.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Custom OAuth2 User Service to handle OAuth2 user information retrieved from providers (e.g. Google).
 * It integrates with the database to find or create User and OauthAccount records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final StudentRepository studentRepository;

    /**
     * Loads user info from OAuth2 provider and processes it (creates/updates DB records).
     * @param userRequest the OAuth2 user request
     * @return CustomOAuth2User containing the user's email and role
     * @throws OAuth2AuthenticationException if authentication fails
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("Loading OAuth2 user from provider: {}", userRequest.getClientRegistration().getRegistrationId());
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processOAuth2User(userRequest, oAuth2User);
    }

    /**
     * Processes the retrieved OAuth2 user by checking DB and linking the account.
     * @param oAuth2UserRequest the original request
     * @param oAuth2User the retrieved OAuth2 user
     * @return CustomOAuth2User mapped with the application's User entity
     */
    private OAuth2User processOAuth2User(OAuth2UserRequest oAuth2UserRequest, OAuth2User oAuth2User) {
        String providerName = oAuth2UserRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfoInternal oAuth2UserInfoInternal = create(providerName, oAuth2User.getAttributes());

        if (oAuth2UserInfoInternal.getEmail() == null || oAuth2UserInfoInternal.getEmail().isEmpty()) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        User user = userRepository.findByEmail(oAuth2UserInfoInternal.getEmail());

        if (user != null) {
            log.info("Found existing user with email: {}", user.getEmail());
            // Optionally update user info here if needed
            boolean updated = false;
            if (user.getBio() == null && oAuth2UserInfoInternal.getBio() != null) {
                user.setBio(oAuth2UserInfoInternal.getBio());
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
            }
            if (oAuth2UserInfoInternal.getHtmlUrl() != null) {
                Student student = studentRepository.findByUser_UserId(user.getUserId());
                if (student == null) {
                    student = Student.builder().user(user).githubProfile(oAuth2UserInfoInternal.getHtmlUrl()).build();
                    studentRepository.save(student);
                } else if (student.getGithubProfile() == null) {
                    student.setGithubProfile(oAuth2UserInfoInternal.getHtmlUrl());
                    studentRepository.save(student);
                }
            }
        } else {
            log.info("Registering new user via OAuth2 for email: {}", oAuth2UserInfoInternal.getEmail());
            user = registerNewOAuth2User(oAuth2UserInfoInternal);
        }

        // Link OauthAccount if it doesn't exist
        Optional<OauthAccount> oauthAccountOpt = oauthAccountRepository.findByProviderIdAndProviderName(
                oAuth2UserInfoInternal.getProviderId(), providerName);

        if (oauthAccountOpt.isEmpty()) {
            log.info("Linking new OAuth account for user: {}", user.getEmail());
            OauthAccount newOauthAccount = OauthAccount.builder()
                    .user(user)
                    .providerId(oAuth2UserInfoInternal.getProviderId())
                    .providerName(providerName)
                    .build();
            oauthAccountRepository.save(newOauthAccount);
        }

        return new CustomOAuth2User(oAuth2User, user.getEmail(), user.getRole().name());
    }

    /**
     * Registers a new User entity from the OAuth2 info.
     * @param oAuth2UserInfoInternal the standardized OAuth2 user info
     * @return the saved User entity
     */
    private User registerNewOAuth2User(OAuth2UserInfoInternal oAuth2UserInfoInternal) {
        User user = User.builder()
                .email(oAuth2UserInfoInternal.getEmail())
                .fullName(oAuth2UserInfoInternal.getFullName())
                .bio(oAuth2UserInfoInternal.getBio())
                .role(UserRole.STUDENT)
                .build();
        User savedUser = userRepository.save(user);
        Student student = Student.builder()
                .user(savedUser)
                .githubProfile(oAuth2UserInfoInternal.getHtmlUrl())
                .build();
        studentRepository.save(student);
        return savedUser;
    }

    /**
     * Creates the correct OAuth2UserInfoInternal instance based on the provider name.
     *
     * @param providerName OAuth2 provider name (google, github)
     * @param attributes   Raw attributes from OAuth2 provider
     * @return OAuth2UserInfoInternal implementation
     * @throws ResourceNotFoundException if provider is not supported
     */
    private OAuth2UserInfoInternal create(String providerName, Map<String, Object> attributes) {
        return switch (providerName.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "github" -> new GitHubOauth2UserInfo(attributes);
            default -> throw new ResourceNotFoundException("Unknown provider name: " + providerName);
        };
    }
}
