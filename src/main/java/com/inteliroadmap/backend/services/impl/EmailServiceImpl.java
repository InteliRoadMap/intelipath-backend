package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.services.EmailService;
import com.inteliroadmap.backend.utils.EmailUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

//    @Override
//    public void sendOtpEmail(String email, String fullName, String otp) {
//        log.info("Email Module: Preparing OTP email for {}", email);
//
//        try {
//            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
//            org.springframework.mail.javamail.MimeMessageHelper helper =
//                    new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
//
//            String htmlContent = com.inteliroadmap.backend.utils.EmailUtil.RESET_PASSWORD_OTP.formatted(fullName, otp);
//            helper.setTo(email);
//
//            helper.setSubject("🔐 Password Reset OTP - InteliPath");
//            helper.setText(htmlContent, true);
//
//            mailSender.send(message);
//            log.info("Email Module: OTP email successfully sent to {}", email);
//
//        } catch (jakarta.mail.MessagingException e) {
//            log.error("Email Module: Failed to send OTP email", e);
//            throw new RuntimeException("Email Module: Failed to send OTP email");
//        }
//    }

    /**
     * Sends a general notification email to the specified email address.
     * 
     * @param email The recipient's email address
     */
    @Override
    public void sendFeedbackNotificationEmail(String email, String receiverName, String senderName, String content, List<MultipartFile> attachments) {
        log.info("EmailServiceImpl: Preparing Feedback Notification email for {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            String htmlContent = EmailUtil.FEEDBACK_NOTIFICATION_EMAIL.formatted(receiverName, senderName, content);
            helper.setTo(email);

            helper.setSubject("You Have A Feedback Notification !!");
            helper.setText(htmlContent, true);

            if (attachments != null && !attachments.isEmpty()) {
                for (MultipartFile file : attachments) {
                    if (!file.isEmpty() && file.getOriginalFilename() != null) {
                        helper.addAttachment(
                            file.getOriginalFilename(),
                            new ByteArrayResource(file.getBytes())
                        );
                    }
                }
            }

            mailSender.send(message);
            log.info("EmailServiceImpl: Notification email successfully sent to {}", email);

        } catch (MessagingException e) {
            log.error("EmailServiceImpl: Failed to send Notification email", e);
            throw new RuntimeException("Email Module: Failed to send Notification email");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

