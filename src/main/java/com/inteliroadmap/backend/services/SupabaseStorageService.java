package com.inteliroadmap.backend.services;

import org.springframework.web.multipart.MultipartFile;

public interface SupabaseStorageService {

    String uploadAvatar(MultipartFile file, String userId);

    String uploadChatFile(MultipartFile file);

    String uploadTranscript(MultipartFile file, String userId);
}
