package com.inteliroadmap.backend.domain.projection;

import java.util.UUID;

public interface StudentInfoProjection {
    UUID getStudentId();
    String getFullName();
    String getEmail();
    String getUniversity();
    String getCareerName();
}
