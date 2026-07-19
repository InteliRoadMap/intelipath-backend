package com.inteliroadmap.backend.services;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface EmailService {

    void sendFeedbackNotificationEmail(String email, String receiverName, String senderName, String content, List<MultipartFile> attachments);

    /**
     * Sends the password-reset magic link.
     *
     * @param email     recipient address
     * @param fullName  recipient display name for the greeting
     * @param resetLink the one-time reset URL (frontend route with the raw token)
     */
    void sendPasswordResetEmail(String email, String fullName, String resetLink);
}
