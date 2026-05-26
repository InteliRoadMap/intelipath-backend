package com.inteliroadmap.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Send an OTP code to the specified email address
     * @param email User's email address
     * @param otp The 6-digit OTP code
     */
    public void sendOtpEmail(String email, String otp) {
        log.info("EmailService: Preparing to send OTP email to {}", email);
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Password Reset OTP - InteliPath");
        message.setText("Your OTP code for password reset is: " + otp + "\n\nThis code expires in 5 minutes. If you did not request this, please ignore this email.");

        mailSender.send(message);
        
        log.info("EmailService: OTP email successfully sent to {}", email);
    }
}
