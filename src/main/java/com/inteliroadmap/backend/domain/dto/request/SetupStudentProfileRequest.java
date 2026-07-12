package com.inteliroadmap.backend.domain.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class SetupStudentProfileRequest {

    private UUID universityId;

    private String universityName;

    private Integer yearOfAdmission;

    private String major;

    private UUID careerId;

    private String githubProfile;

    private String bio;

    private String yob;
}
