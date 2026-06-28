package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.UpdateProfileResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.CounselorMapper;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface CounselorService {

    public CounselorResponse getCareerStatistics() ;

    public CounselorResponse getStudentsMissingSkills(String searchName) ;

    public CounselorResponse getAllFeedbacksSentToMe() ;

    public CounselorResponse getStudentInfos(String search) ;

    public CounselorResponse getStudentStatisticAndFeedback(UUID studentId) ;

    public CounselorResponse createFeedback(CreateFeedbackRequest request) ;

    public UpdateProfileResponse getProfile() ;

    public UpdateProfileResponse updateProfile(UpdateProfileRequest request) ;
}
