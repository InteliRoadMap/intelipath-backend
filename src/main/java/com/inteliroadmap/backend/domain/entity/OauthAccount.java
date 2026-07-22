package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "oauth_accounts")

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OauthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "oauth_acc_id")
    private UUID oauthAccountId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    /**
     * Provider OAuth access token, encrypted at rest via TokenCipher (never plaintext).
     * Populated for GitHub logins with the 'repo' scope so the portfolio Sync flow can
     * list the user's private repositories. Null for providers/logins without a stored token.
     */
    @Column(name = "access_token_enc", columnDefinition = "TEXT")
    private String accessTokenEnc;

    /** Space-separated scopes actually granted for the stored token, for reference/diagnostics. */
    @Column(name = "token_scopes")
    private String tokenScopes;
}

