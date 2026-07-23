package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.User;

/**
 * Decides the portfolio slug for a student.
 *
 * Every student has one: it is the only stable, human-readable way to address a
 * portfolio, and the mentor and public portfolio routes are built on it.
 */
public interface PortfolioSlugService {

    /**
     * Picks a slug for a user that no other student holds.
     *
     * @param user the student's user account
     * @return a slug that is free at the time of the call
     */
    String allocateFor(User user);
}
