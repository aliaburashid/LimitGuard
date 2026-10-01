package com.example.limitguard.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// Handles emails sent by LimitGuard
@Service
public class EmailService {

    // gives this service access to Springs email sender
    @Autowired
    private JavaMailSender mailSender;

    public void SendVerificationEmail(String email, String token) {

        // Create the verification link using the user's token
        String verificationLink = "http://localhost:8080/api/auth/users/verify-email?token=" + token;

        try {

            // Create the email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // set who will receive the email
            helper.setTo(email);

            // set the email subject
            helper.setSubject("Verify your LimitGuard account");

            // set the email message
            String emailMessage = """
                    <html>
                    <body style="margin: 0; padding: 0; background-color: #f4f6f8;
                                 font-family: Arial, sans-serif;">

                        <div style="max-width: 600px; margin: 40px auto;
                                    background-color: white; padding: 40px;
                                    border-radius: 10px;">

                            <h1 style="color: #1f2937;">
                                LimitGuard
                            </h1>

                            <h2 style="color: #1f2937;">
                                Verify your email
                            </h2>

                            <p style="color: #4b5563;">
                                Hi %s,
                            </p>

                            <p style="color: #4b5563; line-height: 1.6;">
                                Thanks for registering to our LimitGuard App.
                                Please verify your email by clicking the button below:
                            </p>

                            <div style="margin: 30px 0;">
                                <a href="%s"
                                   style="background-color: #1f2937;
                                          color: white;
                                          padding: 14px 24px;
                                          text-decoration: none;
                                          border-radius: 6px;
                                          display: inline-block;">
                                    Verify Email
                                </a>
                            </div>

                            <p style="color: #6b7280; font-size: 14px;">
                                This verification link expires in 24 hours.
                            </p>

                            <hr style="border: none;
                                       border-top: 1px solid #e5e7eb;
                                       margin: 30px 0;">

                            <p style="color: #9ca3af; font-size: 12px;">
                                LimitGuard - Credit Limit & Exposure Management
                            </p>

                        </div>

                    </body>
                    </html>
                    """.formatted(email, verificationLink);

            // true means the email message contains HTML
            helper.setText(emailMessage, true);

            // send the email
            mailSender.send(message);

        } catch (MessagingException exception) {
            throw new RuntimeException("Unable to create verification email");
        }
    }
}