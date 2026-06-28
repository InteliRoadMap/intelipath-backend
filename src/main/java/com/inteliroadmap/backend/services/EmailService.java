package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.utils.EmailUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

public interface EmailService {

    public void sendOtpEmail(String email, String fullName, String otp) ;
}
