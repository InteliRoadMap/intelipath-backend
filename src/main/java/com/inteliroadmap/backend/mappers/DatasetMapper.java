package com.inteliroadmap.backend.mappers;

import java.util.UUID;

public interface DatasetMapper {
    String getFullName();
    String getSkillName();

    UUID getSkillId();
    UUID getStudentId();
    UUID getCareerId();
}
