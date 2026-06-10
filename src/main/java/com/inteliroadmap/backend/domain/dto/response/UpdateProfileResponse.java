package com.inteliroadmap.backend.domain.dto.response;

import com.inteliroadmap.backend.domain.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileResponse {
    private User user;
    private AcademicCounselor academicCounselor;
    private IndustryMentor industryMentor;
    private Student student;
}
