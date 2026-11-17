package com.example.activityservice.service;

import com.example.activityservice.dto.CreditRequestEmailDelivery;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@ConditionalOnProperty(name = "activity.email.delivery-enabled", havingValue = "true")
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${activity.email.from}") String fromAddress) {
        if (fromAddress.isBlank()) {
            throw new IllegalArgumentException("An SMTP sender address is required");
        }
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendCreditRequestEmail(String recipient, CreditRequestEmailDelivery delivery) {
        long requestId = delivery.getCreditRequestId();
        switch (delivery.getEventType()) {
            case "CREDIT_REQUEST_APPROVED" -> send(recipient, "Your LimitGuard credit request was approved",
                    "Credit request approved", "Your credit request <strong>#" + requestId
                            + "</strong> has been approved by a Risk Officer.",
                    "Credit capacity reserved", "The requested credit capacity has now been reserved.", "#f0fdf4");
            case "CREDIT_REQUEST_REJECTED" -> send(recipient, "Your LimitGuard credit request was rejected",
                    "Credit request rejected", "Your credit request <strong>#" + requestId
                            + "</strong> has been rejected by a Risk Officer.",
                    "Reason for rejection", escapedReason(delivery.getReason()), "#fef2f2");
            case "CREDIT_REQUEST_EXPIRED" -> send(recipient, "Your LimitGuard credit reservation has expired",
                    "Credit reservation expired", "The reserved credit capacity for your credit request <strong>#"
                            + requestId + "</strong> has expired.",
                    "Reservation released", "The capacity is no longer reserved for this request.", "#f4f6f8");
            default -> throw new IllegalArgumentException("Unsupported credit request email event");
        }
    }

    private String escapedReason(String reason) {
        // Display rejection reasons as text, even when they contain HTML tags.
        return reason == null || reason.isBlank() ? "No reason provided." : HtmlUtils.htmlEscape(reason);
    }

    private void send(String recipient, String subject, String title, String description,
            String detailTitle, String detail, String detailColor) {
        String html = """
                <html>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif">
                    <div style="max-width:600px;margin:40px auto;background:white;padding:40px;border-radius:10px">
                        <h1 style="color:#1f2937">LimitGuard</h1>
                        <hr style="border:0;border-top:1px solid #e5e7eb">
                        <h2 style="color:#1f2937">%s</h2>
                        <p style="color:#4b5563;line-height:1.6">%s</p>
                        <div style="background:%s;padding:20px;border-radius:6px">
                            <strong>%s</strong>
                            <p style="line-height:1.6">%s</p>
                        </div>
                        <p style="color:#6b7280;font-size:12px">LimitGuard - Credit Limit &amp; Exposure Management</p>
                    </div>
                </body>
                </html>
                """.formatted(title, description, detailColor, detailTitle, detail);

        try {
            // Build the message using the verified address returned by Auth.
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new MailPreparationException("Unable to prepare credit request email", exception);
        }
    }
}
