package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "oauth_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OauthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "oauth_acc_id")
    private UUID oauthAccId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_oa_user"))
    private User user;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;
}
