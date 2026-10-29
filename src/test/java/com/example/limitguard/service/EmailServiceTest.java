package com.example.limitguard.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// Verifies message dispatch to the mail sender; never contacts an SMTP server.
// Direct method calls do not establish asynchronous execution or inbox delivery.
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {
    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private MimeMessage message;

    @BeforeEach
    void prepareMessage() {
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    @Test
    void shouldDispatchApprovalMessageOnceToRequester() throws Exception {
        emailService.SendCreditRequestApprovedEmail("requester@example.test", 42L);

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(message);
        verifyNoMoreInteractions(mailSender);
        assertEquals("requester@example.test", message.getAllRecipients()[0].toString());
        assertTrue(message.getSubject().toLowerCase().contains("approved"));
    }

    @Test
    void shouldDispatchRejectionMessageOnceToRequester() throws Exception {
        emailService.SendCreditRequestRejectedEmail(
                "requester@example.test", 43L, "Financial covenants require review");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(message);
        verifyNoMoreInteractions(mailSender);
        assertEquals("requester@example.test", message.getAllRecipients()[0].toString());
        assertTrue(message.getSubject().toLowerCase().contains("rejected"));
    }
}
