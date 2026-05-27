package com.inteliroadmap.backend.services;

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

    public void sendOtpEmail(String email, String otp) {

        log.info("Preparing OTP email for {}", email);

        try {

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("🔐 Password Reset OTP - InteliPath");

            String htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <style>
                            body {
                                font-family: 'Inter', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                                background: linear-gradient(180deg, #eef2ff 0%%, #f8fafc 100%%);
                                margin: 0;
                                padding: 0;
                                color: #20222b;
                            }

                            .container {
                                max-width: 580px;
                                margin: 48px auto;
                                background: #ffffff;
                                border-radius: 28px;
                                overflow: hidden;
                                box-shadow: 0 24px 80px rgba(15, 23, 42, 0.12);
                                border: 1px solid rgba(99, 102, 241, 0.14);
                            }

                            .header {
                                background: linear-gradient(135deg, #1FE0F0, #6AE9F7);
                                color: white;
                                padding: 36px 28px;
                                text-align: center;
                            }

                            .header h1 {
                                margin: 0;
                                font-size: 30px;
                                letter-spacing: -0.03em;
                                line-height: 1.1;
                            }

                            .header p {
                                margin: 12px auto 0;
                                max-width: 420px;
                                font-size: 16px;
                                opacity: 0.88;
                            }

                            .content {
                                padding: 38px 32px 46px;
                                text-align: center;
                            }

                            .content h2 {
                                margin: 0 0 14px;
                                font-size: 24px;
                            }

                            .content p {
                                margin: 0 auto 28px;
                                max-width: 470px;
                                line-height: 1.7;
                                color: #4b5563;
                            }

                            .otp-box {
                                display: inline-flex;
                                align-items: center;
                                justify-content: center;
                                background: linear-gradient(180deg, #eef2ff 0%%, #ffffff 100%%);
                                padding: 22px 44px;
                                border-radius: 18px;
                                font-size: 34px;
                                font-weight: 700;
                                letter-spacing: 10px;
                                color: #008FFF;
                                margin: 16px 0;
                                border: 1px solid rgba(79, 70, 229, 0.16);
                                box-shadow: 0 20px 40px rgba(79, 70, 229, 0.08);
                            }

                            .info-box {
                                margin: 20px auto 0;
                                max-width: 520px;
                                background: #EDF8FF;
                                border-left: 4px solid #29E8FF;
                                border-radius: 14px;
                                padding: 18px 22px;
                                color: #008FFF;
                                text-align: left;
                                line-height: 1.65;
                                font-size: 15px;
                            }

                            .countdown-text {
                                margin-top: 22px;
                                font-size: 16px;
                                color: #374151;
                            }

                            .countdown-text strong {
                                color: #008FFF;
                            }

                            .warning {
                                margin-top: 28px;
                                font-size: 14px;
                                color: #6b7280;
                                max-width: 520px;
                                margin-left: auto;
                                margin-right: auto;
                            }

                            .footer {
                                background: #f8fafc;
                                text-align: center;
                                padding: 22px 24px;
                                font-size: 13px;
                                color: #6b7280;
                                border-top: 1px solid rgba(148, 163, 184, 0.18);
                            }
                        </style>
                    </head>

                    <body>

                        <div class="container">

                            <div class="header">
                                <h1>InteliPath</h1>
                                <p>Password Reset Request</p>
                            </div>

                            <div class="content">

                                <h2>Hello 👋</h2>

                                <p>
                                    We received a request to reset your password.<br/>
                                    Use the OTP code below to continue:
                                </p>

                                <div class="otp-box">
                                    %s
                                </div>

                                <div class="info-box">
                                    Your verification code is active for the next 2 minutes.<br/>
                                    Enter it on the secure page to finish resetting your password.
                                </div>

                                <p class="countdown-text">
                                    This OTP will expire in <strong>02:00</strong>.
                                </p>

                                <div class="warning">
                                    If you did not request a password reset, you can safely ignore this message.
                                </div>

                            </div>

                            <div class="footer">
                                © 2026 InteliPath. All rights reserved.
                            </div>

                        </div>

                    </body>
                    </html>
                    """.formatted(otp);

            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("OTP email successfully sent to {}", email);

        } catch (MessagingException e) {

            log.error("Failed to send OTP email", e);

            throw new RuntimeException("Failed to send OTP email");
        }
    }
}

