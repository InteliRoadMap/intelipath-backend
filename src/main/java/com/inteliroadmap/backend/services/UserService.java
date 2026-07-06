package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;

public interface UserService {

    UserResponse getCurrentUser() ;

    UserResponse getUserByEmail(UserRequest userRequest) ;

    UserResponse setupUserProfile(SetupUserProfileRequest request) ;

    UserResponse updateAvatar(org.springframework.web.multipart.MultipartFile file) ;
}
