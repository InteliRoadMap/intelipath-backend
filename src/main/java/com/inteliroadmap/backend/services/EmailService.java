package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.utils.EmailUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String email, String fullName, String otp) {
        log.info("Email Module: Preparing OTP email for {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            String htmlContent = EmailUtil.RESET_PASSWORD_OTP.formatted(fullName, otp);
            helper.setTo(email);

            helper.setSubject("🔐 Password Reset OTP - InteliPath");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email Module: OTP email successfully sent to {}", email);

        } catch (MessagingException e) {
            log.error("Email Module: Failed to send OTP email", e);
            throw new RuntimeException("Email Module: Failed to send OTP email");
        }
    }
}

