package com.example.activityservice.service;

import com.example.activityservice.dto.CreditRequestEmailDelivery;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @ParameterizedTest
    @CsvSource({
            "CREDIT_REQUEST_APPROVED, Your LimitGuard credit request was approved, Credit capacity reserved",
            "CREDIT_REQUEST_REJECTED, Your LimitGuard credit request was rejected, Reason for rejection",
            "CREDIT_REQUEST_EXPIRED, Your LimitGuard credit reservation has expired, Reservation released"
    })
    void sendsTheCorrectHtmlToTheVerifiedRecipient(String eventType, String subject, String detail) throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var emails = new EmailService(sender, "sender@example.test");
        var delivery = new CreditRequestEmailDelivery(1L, 5L, 125L, 15L, eventType, "<script>&");

        emails.sendCreditRequestEmail("requester@example.test", delivery);

        verify(sender).send(message);
        assertThat(message.getAllRecipients()).singleElement()
                .satisfies(address -> assertThat(address.toString()).isEqualTo("requester@example.test"));
        assertThat(message.getSubject()).isEqualTo(subject);
        String body = (String) message.getContent();
        assertThat(body).contains(detail, "#125").doesNotContain("<script>");
        if ("CREDIT_REQUEST_REJECTED".equals(eventType)) {
            assertThat(body).contains("&lt;script&gt;&amp;");
        }
    }
}
