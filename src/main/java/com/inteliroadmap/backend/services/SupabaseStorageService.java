package com.inteliroadmap.backend.services;

import org.springframework.web.multipart.MultipartFile;

public interface SupabaseStorageService {

    String uploadAvatar(MultipartFile file, String userId);

    String uploadChatFile(MultipartFile file);

    String uploadTranscript(MultipartFile file, String userId);

    /**
     * Mirrors a harvested course file into the private course-materials bucket.
     *
     * Unlike the other uploads this takes raw bytes: the file is fetched server-side, not
     * posted by a user. It returns the object path rather than a public URL — the whole
     * point of hosting the file is that access goes through {@link #signCourseMaterial}
     * after the caller has been checked.
     *
     * @param content     the file bytes
     * @param objectPath  key within the bucket, e.g. {@code PRJ301/1_PRJ301.zip}
     * @param contentType MIME type to store the object with
     * @return the object path that was written
     */
    String uploadCourseMaterial(byte[] content, String objectPath, String contentType);

    /**
     * Short-lived signed URL for a mirrored course file.
     *
     * The bucket is private, so this URL is the only way to read the object and it expires.
     * Mint one per request, after authorising the student — never store it.
     *
     * @param objectPath       key returned by {@link #uploadCourseMaterial}
     * @param expiresInSeconds lifetime of the link
     */
    String signCourseMaterial(String objectPath, int expiresInSeconds);
}
