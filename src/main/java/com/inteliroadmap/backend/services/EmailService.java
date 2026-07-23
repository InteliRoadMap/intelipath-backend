package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.enums.UserRole;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface EmailService {

    /**
     * Notifies a student that someone reviewed their work.
     *
     * @param senderRole who wrote it. The email names the sender's role to the reader, so a
     *                   mentor's review must not arrive introduced as a counselor's.
     */
    void sendFeedbackNotificationEmail(String email, String receiverName, String senderName,
                                       UserRole senderRole, String content,
                                       List<MultipartFile> attachments);

    /**
     * Sends the password-reset magic link.
     *
     * @param email     recipient address
     * @param fullName  recipient display name for the greeting
     * @param resetLink the one-time reset URL (frontend route with the raw token)
     */
    void sendPasswordResetEmail(String email, String fullName, String resetLink);
}
