package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.ExportStudentListRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.UpdateProfileResponse;

import java.util.UUID;

public interface CounselorService {

    CounselorResponse getCareerStatistics();

    CounselorResponse getStudentsMissingSkills(String searchName);

    CounselorResponse getAllFeedbacksSentByMe();

    CounselorResponse getStudentInfos(String search, int page, int size);

    CounselorResponse getStudentStatisticAndFeedback(UUID studentId);

    UpdateProfileResponse getProfile();

    UpdateProfileResponse updateProfile(UpdateProfileRequest request);

    byte[] exportStudentList(ExportStudentListRequest request);
}
