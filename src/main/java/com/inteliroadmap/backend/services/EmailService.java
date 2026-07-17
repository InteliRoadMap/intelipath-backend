package com.inteliroadmap.backend.services;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface EmailService {

    void sendFeedbackNotificationEmail(String email, String receiverName, String senderName, String content, List<MultipartFile> attachments);
}
