package com.inteliroadmap.backend.utils;

import java.util.Random;

public class EmailUtil {

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

    public static final String FEEDBACK_NOTIFICATION_EMAIL = """
            <!doctype html>
           <html lang="vi">
             <head>
               <meta charset="UTF-8" />
               <meta name="viewport" content="width=device-width, initial-scale=1.0" />
               <style>
                 body {
                   font-family: "Inter", "Segoe UI", system-ui, -apple-system, sans-serif;
                   background: #f1f5f9;
                   margin: 0;
                   padding: 40px 16px;
                   color: #1f2937;
                   -webkit-font-smoothing: antialiased;
                 }
    
                 .wrap {
                   max-width: 520px;
                   margin: 0 auto;
                   background: #ffffff;
                   border-radius: 20px;
                   overflow: hidden;
                   box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
                 }
    
                 .header {
                   background: linear-gradient(135deg, #0ea5e9 0%%, #2563eb 100%%);
                   padding: 16px;
                   position: relative;
                   display: flex;
                   flex-direction: column;
                   align-items: center;
                   font-size: 28px;
                   font-weight: 800;
                   color: #fff;
                   letter-spacing: -0.03em;
                   line-height: 1;
                 }
    
                 .body {
                   padding: 32px 32px 28px;
                 }
    
                 .greeting {
                   font-size: 19px;
                   font-weight: 600;
                   color: #111827;
                   margin: 0 0 8px;
                 }
    
                 .sub {
                   font-size: 13.5px;
                   color: #6b7280;
                   line-height: 1.65;
                   margin: 0 0 22px;
                 }
    
                 .card {
                   background: #f8fafc;
                   border: 1px solid #e2e8f0;
                   border-radius: 12px;
                   padding: 18px 22px;
                   margin-bottom: 18px;
                 }
    
                 .clabel {
                   font-size: 10.5px;
                   text-transform: uppercase;
                   letter-spacing: 0.09em;
                   font-weight: 700;
                   color: #1d4ed8;
                   margin-bottom: 10px;
                   display: flex;
                   align-items: center;
                   gap: 5px;
                 }
    
                 .msg {
                   font-size: 14.5px;
                   color: #1e293b;
                   line-height: 1.65;
                   margin: 0;
                 }
    
                 .info {
                   background: #eff6ff;
                   border-left: 3px solid #3b82f6;
                   border-radius: 0 8px 8px 0;
                   padding: 13px 17px;
                   font-size: 13px;
                   color: #1e40af;
                   line-height: 1.55;
                 }
    
                 .foot {
                   background: #f9fafb;
                   border-top: 1px solid #f1f5f9;
                   text-align: center;
                   padding: 16px;
                   font-size: 11.5px;
                   color: #9ca3af;
                 }
               </style>
             </head>
             <body>
               <div class="wrap">
                 <div class="header">
                   <p>InteliPath</p>
                 </div>
    
                 <div class="body">
                   <p class="greeting">Hello, %s 👋</p>
                   <p class="sub">
                     Your counselor <strong>%s</strong> has just sent you new feedback regarding your current academic progress. Please review the message below carefully.
                   </p>
    
                   <div class="card">
                     <div class="clabel">💬 Counselor's message</div>
                     <p class="msg">%s</p>
                   </div>
    
                   <div class="info">
                     💡 <strong>Next steps:</strong> Review this feedback and take the necessary actions to optimise your learning journey.
                   </div>
                 </div>
    
                 <div class="foot">© 2026 InteliPath · All rights reserved</div>
               </div>
             </body>
           </html>
            """;
}
