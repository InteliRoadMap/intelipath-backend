package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.AccountType;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.services.PortfolioSlugService;
import com.inteliroadmap.backend.utils.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioSlugServiceImpl implements PortfolioSlugService {

    /** Enough attempts that exhausting them means something is wrong, not unlucky. */
    private static final int MAX_ATTEMPTS = 100;

    private final StudentRepository studentRepository;

    @Override
    public String allocateFor(User user) {
        String base = baseFor(user);
        if (isFree(base, user)) {
            return base;
        }

        // The username-derived slug carries no id, so a collision is possible; fall
        // back to the same suffix the generated slugs already use.
        String withId = base + "-" + user.getUserId().toString().substring(0, 8);
        if (isFree(withId, user)) {
            return withId;
        }

        for (int n = 2; n < MAX_ATTEMPTS; n++) {
            String candidate = withId + "-" + n;
            if (isFree(candidate, user)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free portfolio slug for user " + user.getUserId());
    }

    /**
     * A slug the user already holds counts as free.
     *
     * Without this, re-allocating for someone who already has their ideal slug
     * would collide with their own row and hand back a suffixed variant, so the
     * slug would drift on every call.
     */
    private boolean isFree(String slug, User user) {
        return studentRepository.findByPortfolioSlug(slug)
                .map(existing -> existing.getUserId().equals(user.getUserId()))
                .orElse(true);
    }

    /**
     * An FPT account's slug is its username: counselor-provisioned, already unique,
     * and something the student recognises. Everyone else is named after themselves
     * plus part of their id, because OAuth accounts arrive with no username at all.
     *
     * Falls through to the generated form whenever the username is missing, which
     * covers FPT rows that were created without a local credential.
     */
    private String baseFor(User user) {
        if (user.getAccountType() == AccountType.FPT) {
            String fromUsername = SlugUtils.slugify(user.getUsername());
            if (!fromUsername.isEmpty()) {
                return fromUsername;
            }
        }
        return SlugUtils.generateSlug(user.getFullName(), user.getUserId());
    }
}
