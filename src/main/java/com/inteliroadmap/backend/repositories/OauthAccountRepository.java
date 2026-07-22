package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.OauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OauthAccountRepository extends JpaRepository<OauthAccount, UUID> {

    Optional<OauthAccount> findByProviderIdAndProviderName(String providerId, String providerName);

    Optional<OauthAccount> findByUser_UserIdAndProviderName(UUID userId, String providerName);

}
