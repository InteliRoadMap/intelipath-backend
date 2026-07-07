package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorResponse;
import com.inteliroadmap.backend.domain.projection.StudentInfoProjection;
import com.inteliroadmap.backend.domain.dto.response.student.UpdateProfileResponse;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.CounselorMapper;
import com.inteliroadmap.backend.services.CounselorService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface CounselorService {

    public CounselorResponse getCareerStatistics() ;

    public CounselorResponse getStudentsMissingSkills(String searchName) ;

    public CounselorResponse getAllFeedbacksSentToMe() ;

    public CounselorResponse getStudentInfos(String search, int page, int size) ;

    public CounselorResponse getStudentStatisticAndFeedback(UUID studentId) ;

    public UpdateProfileResponse getProfile() ;

    public UpdateProfileResponse updateProfile(UpdateProfileRequest request) ;
}
