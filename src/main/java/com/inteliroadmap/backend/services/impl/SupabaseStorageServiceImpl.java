package com.inteliroadmap.backend.services.impl;

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

/**
 * Implementation of the {@link SupabaseStorageService}.
 * Provides services for uploading files (such as avatars and chat files) to Supabase Storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseStorageServiceImpl implements SupabaseStorageService {

    private final RestTemplate externalApiRestTemplate;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final String AVATAR_BUCKET = "avatars";
    private final String CHAT_BUCKET = "chat_files";
    private final String TRANSCRIPT_BUCKET = "transcripts";

    /**
     * Uploads an avatar image to the Supabase avatars bucket for the given user ID.
     *
     * @param file the multipart file containing the avatar image
     * @param userId the ID of the user for whom the avatar is being uploaded
     * @return the public URL of the uploaded avatar
     * @throws RuntimeException if the upload fails
     */
    @Override
    public String uploadAvatar(MultipartFile file, String userId) {
        log.info("SupabaseStorageServiceImpl: Uploading avatar for user ID: {}", userId);
        try {
            // Shared RestTemplate carries connect/read timeouts (see HttpClientConfig).
            RestTemplate restTemplate = externalApiRestTemplate;

            // Construct the unique file name using user ID and file extension
            String fileExtension = getFileExtension(file.getOriginalFilename());
            String fileName = userId + (fileExtension.isEmpty() ? ".jpg" : fileExtension);
            String url = supabaseUrl + "/storage/v1/object/" + AVATAR_BUCKET + "/" + fileName;

            // Prepare HTTP headers with bearer auth and content type
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(supabaseKey);
            headers.setContentType(MediaType.valueOf(file.getContentType() != null ? file.getContentType() : "image/jpeg"));
            
            // Allow overwriting existing files
            headers.set("x-upsert", "true");

            // Build request entity with file bytes and headers
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

            // Execute POST request to Supabase API
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                // Return the public URL of the uploaded image if successful
                return supabaseUrl + "/storage/v1/object/public/" + AVATAR_BUCKET + "/" + fileName;
            } else {
                log.error("SupabaseStorageServiceImpl: Supabase API Error: {}", response.getBody());
                throw new RuntimeException("Failed to upload file to Supabase");
            }
        } catch (IOException e) {
            log.error("SupabaseStorageServiceImpl: Failed to read MultipartFile", e);
            throw new RuntimeException("Failed to read file", e);
        } catch (Exception e) {
            log.error("SupabaseStorageServiceImpl: Error communicating with Supabase", e);
            throw new RuntimeException("Failed to upload file to Supabase", e);
        }
    }

    /**
     * Extracts the file extension from the given file name.
     *
     * @param fileName the original file name
     * @return the file extension (including the dot), or an empty string if no extension is found
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * Uploads a chat file to the Supabase chat files bucket.
     *
     * @param file the multipart file containing the chat file
     * @return the public URL of the uploaded chat file
     * @throws RuntimeException if the upload fails
     */
    @Override
    public String uploadChatFile(MultipartFile file) {
        log.info("SupabaseStorageServiceImpl: Uploading chat file: {}", file.getOriginalFilename());
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String fileName = java.util.UUID.randomUUID() + fileExtension;
        return uploadFile(CHAT_BUCKET, fileName, file, "application/pdf", true, "chat file");
    }

    @Override
    public String uploadTranscript(MultipartFile file, String userId) {
        log.info("SupabaseStorageServiceImpl: Uploading transcript for user ID: {}", userId);
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String normalizedExtension = fileExtension.isEmpty() ? ".pdf" : fileExtension;
        String fileName = userId + "/" + java.util.UUID.randomUUID() + normalizedExtension;
        return uploadFile(TRANSCRIPT_BUCKET, fileName, file, "application/pdf", true, "transcript");
    }

    private String uploadFile(
            String bucket,
            String fileName,
            MultipartFile file,
            String defaultContentType,
            boolean upsert,
            String label
    ) {
        try {
            RestTemplate restTemplate = externalApiRestTemplate;
            String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + fileName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(supabaseKey);
            headers.setContentType(MediaType.valueOf(file.getContentType() != null ? file.getContentType() : defaultContentType));
            headers.set("x-upsert", String.valueOf(upsert));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + fileName;
            }

            log.error("SupabaseStorageServiceImpl: Supabase API Error: {}", response.getBody());
            throw new RuntimeException("Failed to upload " + label + " to Supabase");
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("SupabaseStorageServiceImpl: Supabase API Error HTTP {}: {}", e.getStatusCode(), responseBody);
            throw new RuntimeException("Supabase API Error: " + responseBody, e);
        } catch (IOException e) {
            log.error("SupabaseStorageServiceImpl: Failed to read MultipartFile", e);
            throw new RuntimeException("Failed to read file", e);
        } catch (Exception e) {
            log.error("SupabaseStorageServiceImpl: Error communicating with Supabase", e);
            throw new RuntimeException("Failed to upload " + label + " to Supabase: " + e.getMessage(), e);
        }
    }
}
