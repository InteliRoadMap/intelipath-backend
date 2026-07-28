package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.ExportStudentListRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportStudentAccountsRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorDashboardResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CurriculumResponse;
import com.inteliroadmap.backend.domain.dto.response.student.UpdateProfileResponse;

import java.util.UUID;

public interface CounselorService {

    CounselorDashboardResponse getCareerStatistics();

    CounselorDashboardResponse getStudentsMissingSkills(String searchName);

    CounselorDashboardResponse getAllFeedbacksSentByMe();

    CounselorFeedbackResponse getStudentInfos(String search, int page, int size, String career);

    CounselorFeedbackResponse getStudentStatisticAndFeedback(UUID studentId);

    UpdateProfileResponse getProfile();

    UpdateProfileResponse updateProfile(UpdateProfileRequest request);

    byte[] exportStudentList(ExportStudentListRequest request);

    CurriculumResponse getCurriculums();

    void checkStudentEmail(String email);

    byte[] importStudentAccounts(ImportStudentAccountsRequest request);
}
