package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.student.CareerAffinityResponse;

import java.util.List;

/**
 * Suggests careers whose essential skills the student already overlaps.
 *
 * <p>Advisory only. No method here writes {@code students.career_id} — choosing
 * a career stays an action the student takes.
 */
public interface CareerAffinityService {

    /**
     * @param limit how many to return, nearest first; null or &lt;= 0 returns all
     */
    List<CareerAffinityResponse> rankForCurrentStudent(Integer limit);
}
