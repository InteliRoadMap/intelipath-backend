package com.inteliroadmap.backend.services.impl;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseStorageServiceImpl {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final String AVATAR_BUCKET = "avatars";
    private final String CHAT_BUCKET = "chat_files";

    public String uploadAvatar(MultipartFile file, String userId) {
        log.info("SupabaseStorageService: Uploading avatar for user ID: {}", userId);
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            String fileExtension = getFileExtension(file.getOriginalFilename());
            String fileName = userId + (fileExtension.isEmpty() ? ".jpg" : fileExtension);
            String url = supabaseUrl + "/storage/v1/object/" + AVATAR_BUCKET + "/" + fileName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(supabaseKey);
            headers.setContentType(MediaType.valueOf(file.getContentType() != null ? file.getContentType() : "image/jpeg"));
            
            // Allow overwriting existing files
            headers.set("x-upsert", "true");

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return supabaseUrl + "/storage/v1/object/public/" + AVATAR_BUCKET + "/" + fileName;
            } else {
                log.error("Supabase API Error: {}", response.getBody());
                throw new RuntimeException("Failed to upload file to Supabase");
            }
        } catch (IOException e) {
            log.error("Failed to read MultipartFile", e);
            throw new RuntimeException("Failed to read file", e);
        } catch (Exception e) {
            log.error("Error communicating with Supabase", e);
            throw new RuntimeException("Failed to upload file to Supabase", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    public String uploadChatFile(MultipartFile file) {
        log.info("SupabaseStorageService: Uploading chat file: {}", file.getOriginalFilename());
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            String fileExtension = getFileExtension(file.getOriginalFilename());
            // Generate a random UUID to avoid conflicts
            String fileName = java.util.UUID.randomUUID().toString() + fileExtension;
            // Uploading to "chat_files" bucket for clean organization
            String url = supabaseUrl + "/storage/v1/object/" + CHAT_BUCKET + "/" + fileName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(supabaseKey);
            headers.setContentType(MediaType.valueOf(file.getContentType() != null ? file.getContentType() : "application/pdf"));
            
            // Allow overwriting existing files
            headers.set("x-upsert", "true");

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return supabaseUrl + "/storage/v1/object/public/" + CHAT_BUCKET + "/" + fileName;
            } else {
                log.error("Supabase API Error: {}", response.getBody());
                throw new RuntimeException("Failed to upload chat file to Supabase");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Supabase API Error HTTP {}: {}", e.getStatusCode(), responseBody);
            throw new RuntimeException("Supabase API Error: " + responseBody, e);
        } catch (IOException e) {
            log.error("Failed to read MultipartFile", e);
            throw new RuntimeException("Failed to read file", e);
        } catch (Exception e) {
            log.error("Error communicating with Supabase", e);
            throw new RuntimeException("Failed to upload chat file to Supabase: " + e.getMessage(), e);
        }
    }
}
