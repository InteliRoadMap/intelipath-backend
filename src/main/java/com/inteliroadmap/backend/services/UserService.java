package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.ChangePasswordRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.UserResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserResponse getCurrentUser();

    UserResponse getUserByEmail(UserRequest userRequest);

    UserResponse setupUserProfile(SetupUserProfileRequest request);

    UserResponse updateAvatar(MultipartFile file);

    /**
     * Changes the password of the currently authenticated account, of any role. Verifies
     * the current password first, so only an already-authenticated holder of the old
     * password can set a new one.
     */
    void changePassword(ChangePasswordRequest request);
}
