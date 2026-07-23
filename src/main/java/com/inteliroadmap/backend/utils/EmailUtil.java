package com.inteliroadmap.backend.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EmailUtil {

    /**
     * The one accent every InteliPath email uses, matching {@code --color-brand-indigo} in
     * the frontend. Templates are text blocks and cannot interpolate it, so the literal is
     * repeated in the markup below — change it here and in each template together.
     */
    static final String BRAND_ACCENT = "#4f46e5";

    /** Bold inside a feedback body, darkened so headings separate from the text they label. */
    private static final String BOLD_STYLE = " style=\"color:#0f172a; font-weight:700;\"";

    /**
     * Generate a 6-digit random OTP
     * @return 6-digit OTP string
     */
    public static String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    public static final String RESET_PASSWORD_OTP = """
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
                    </div>

                    <div class="content">
                        <h2>Hello %s</h2>
                        <p>
                            We received a request to reset your password.<br/>
                            Use the OTP code below to continue:
                        </p>
                        <div class="otp-box">
                            %s
                        </div>
                        <div class="info-box">
                            Your verification code is active for the next <strong>2 minutes</strong>.<br/>
                            Enter it on the secure page to finish resetting your password.
                        </div>
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
            """;

    /**
     * Password reset email for the magic-link flow. Three placeholders, in order:
     * recipient full name, the reset URL used in the button href, and the same URL
     * shown as a copy-paste fallback. No literal '%' appears in the markup so
     * String.formatted stays safe.
     *
     * <p>Shares its frame with {@link #FEEDBACK_NOTIFICATION_EMAIL}: same wordmark, card,
     * spacing, and the same {@value #BRAND_ACCENT} accent. Two mails from one product
     * arriving in two visual identities is how a reader learns to trust neither.
     */
    public static final String RESET_PASSWORD_LINK = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              </head>
              <body style="margin:0; padding:32px 16px; background:#f8fafc; font-family:'Inter','Segoe UI',system-ui,-apple-system,sans-serif; -webkit-font-smoothing:antialiased;">
                <div style="max-width:520px; margin:0 auto;">

                  <div style="padding:0 4px 18px; font-size:16px; font-weight:700; letter-spacing:-0.01em; color:#4f46e5;">
                    InteliPath
                  </div>

                  <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:14px; overflow:hidden;">
                    <div style="padding:30px 32px 26px;">

                      <h1 style="margin:0 0 14px; font-size:18px; font-weight:700; letter-spacing:-0.01em; color:#0f172a;">Reset your password</h1>
                      <p style="margin:0 0 6px; font-size:14px; line-height:1.65; color:#64748b;">Hi %s,</p>
                      <p style="margin:0 0 24px; font-size:14px; line-height:1.65; color:#64748b;">
                        We received a request to reset your password. Choose a new one using the button below.
                        The link expires in 30 minutes and can be used once.
                      </p>

                      <a href="%s" style="display:inline-block; background:#4f46e5; color:#ffffff; text-decoration:none; font-size:14px; font-weight:600; padding:12px 26px; border-radius:10px;">
                        Reset password
                      </a>

                      <p style="margin:26px 0 6px; font-size:12px; line-height:1.6; color:#94a3b8;">
                        If the button does not work, paste this link into your browser:
                      </p>
                      <p style="margin:0; font-size:12px; line-height:1.6; color:#4f46e5; word-break:break-all;">%s</p>

                      <p style="margin:24px 0 0; padding-top:20px; border-top:1px solid #f1f5f9; font-size:12px; line-height:1.6; color:#94a3b8;">
                        If you did not request this, you can ignore this email &mdash; your password will not change.
                      </p>

                    </div>
                  </div>

                  <p style="margin:18px 4px 0; font-size:11.5px; line-height:1.6; color:#94a3b8;">
                    &copy; 2026 InteliPath
                  </p>

                </div>
              </body>
            </html>
            """;

    /**
     * Feedback notification. Styles are inline rather than in a &lt;style&gt; block because
     * Outlook and several webmail clients drop the block entirely, which would strip this
     * message back to unstyled text. Placeholders, in order: receiver name, sender role,
     * sender name, card label, rendered body.
     *
     * <p>Shares its frame and its {@value #BRAND_ACCENT} accent with
     * {@link #RESET_PASSWORD_LINK}; keep the two in step.
     */
    public static final String FEEDBACK_NOTIFICATION_EMAIL = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              </head>
              <body style="margin:0; padding:32px 16px; background:#f8fafc; font-family:'Inter','Segoe UI',system-ui,-apple-system,sans-serif; -webkit-font-smoothing:antialiased;">
                <div style="max-width:520px; margin:0 auto;">

                  <div style="padding:0 4px 18px; font-size:16px; font-weight:700; letter-spacing:-0.01em; color:#4f46e5;">
                    InteliPath
                  </div>

                  <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:14px; overflow:hidden;">
                    <div style="padding:30px 32px 26px;">

                      <p style="margin:0 0 6px; font-size:16px; font-weight:600; color:#0f172a;">Hello %s,</p>
                      <p style="margin:0 0 24px; font-size:14px; line-height:1.65; color:#64748b;">
                        Your %s <span style="color:#0f172a; font-weight:600;">%s</span> has sent you new feedback.
                      </p>

                      <div style="border-left:3px solid #4f46e5; padding:2px 0 2px 18px;">
                        <div style="margin:0 0 10px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.08em; color:#94a3b8;">%s</div>
                        <div style="font-size:14.5px; line-height:1.7; color:#1e293b;">%s</div>
                      </div>

                    </div>
                  </div>

                  <p style="margin:18px 4px 0; font-size:11.5px; line-height:1.6; color:#94a3b8;">
                    Sign in to InteliPath to reply or see the full history.<br />
                    &copy; 2026 InteliPath
                  </p>

                </div>
              </body>
            </html>
            """;

    /**
     * Escapes text for safe interpolation into an email's HTML. Names and feedback bodies
     * are written by users, so they reach this template as data and must never be able to
     * close a tag or open one of their own.
     */
    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Renders a feedback body written with light Markdown as HTML.
     *
     * <p>Authors write feedback in the app's textarea using {@code **bold**} headings and
     * line breaks, which an HTML email otherwise shows verbatim — a reader was sent
     * "**Strengths:** ... **Areas for Improvement:**" as literal asterisks running together
     * in one line. Only the two constructs people actually type are supported: bold spans,
     * and blank lines as paragraph breaks. Escaping happens first, so the markup this emits
     * is the only markup in the result.
     */
    public static String renderFeedbackBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String escaped = escapeHtml(raw.trim()).replace("\r\n", "\n").replace("\r", "\n");

        // Bold before paragraphs: a **...** span never legitimately straddles a blank line,
        // and matching it first keeps the regex off the tags added below.
        String bolded = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<strong" + BOLD_STYLE + ">$1</strong>");

        // Styles are inline here for the same reason as in the template: a <style> block
        // would be dropped by Outlook, and a <p> falling back to the client's default
        // margin re-opens the spacing this is meant to control.
        List<String> blocks = new ArrayList<>();
        for (String block : bolded.split("\n{2,}")) {
            String paragraph = block.trim();
            if (!paragraph.isEmpty()) {
                blocks.add(paragraph.replace("\n", "<br />"));
            }
        }

        StringBuilder html = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            String margin = i == blocks.size() - 1 ? "0" : "0 0 12px";
            html.append("<p style=\"margin:").append(margin).append(";\">")
                    .append(blocks.get(i))
                    .append("</p>");
        }
        return html.toString();
    }
}
