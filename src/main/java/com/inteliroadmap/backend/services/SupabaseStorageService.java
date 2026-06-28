package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.services.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface SupabaseStorageService {

    public String uploadAvatar(MultipartFile file, String userId) ;

    public String uploadChatFile(MultipartFile file) ;
}
