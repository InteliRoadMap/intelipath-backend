package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CompareStRmSkillRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillResponse;

public interface SkillService {

    SkillResponse getStudentSkills();

    SkillResponse searchSkills(String search);

    SkillResponse importStudentSkills(ImportSkillsRequest request);

    SkillResponse compareWithStudentSkills(CompareStRmSkillRequest request);
}
