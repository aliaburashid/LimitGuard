package com.example.activityservice.service;

import com.example.activityservice.client.NotificationRecipientClient;
import com.example.activityservice.dto.CreditRequestEmailDelivery;
import com.example.activityservice.repository.CreditRequestEmailDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreditRequestEmailDeliveryServiceTest {

    private final CreditRequestEmailDeliveryRepository deliveries = mock(CreditRequestEmailDeliveryRepository.class);
    private final NotificationRecipientClient recipients = mock(NotificationRecipientClient.class);
    private final EmailService emails = mock(EmailService.class);
    private final CreditRequestEmailDeliveryService worker = new CreditRequestEmailDeliveryService(
            deliveries, recipients, emails);
    private final CreditRequestEmailDelivery event = new CreditRequestEmailDelivery(
            42L, 5L, 125L, 15L, "CREDIT_REQUEST_APPROVED", null);

    @BeforeEach
    void queueEvent() {
        when(deliveries.findPendingDeliveries()).thenReturn(List.of(event));
        when(deliveries.claimDelivery(42L)).thenReturn(true);
        when(recipients.findRecipient(15L, 5L)).thenReturn(Optional.of("requester@example.test"));
    }

    @Test
    void marksSentOnlyAfterTheMailSenderReturns() {
        worker.deliverPendingEmails();

        InOrder order = inOrder(deliveries, recipients, emails);
        order.verify(deliveries).claimDelivery(42L);
        order.verify(recipients).findRecipient(15L, 5L);
        order.verify(emails).sendCreditRequestEmail("requester@example.test", event);
        order.verify(deliveries).finishDelivery(42L, "SENT");
    }

    @Test
    void workerThatLosesTheClaimDoesNotLookUpOrSendEmail() {
        when(deliveries.claimDelivery(42L)).thenReturn(false);

        worker.deliverPendingEmails();

        verifyNoInteractions(recipients, emails);
    }

    @Test
    void ineligibleRequesterIsSkipped() {
        when(recipients.findRecipient(15L, 5L)).thenReturn(Optional.empty());

        worker.deliverPendingEmails();

        verify(deliveries).finishDelivery(42L, "SKIPPED");
        verifyNoInteractions(emails);
    }

    @Test
    void authOutageReturnsEventToPendingWithoutCallingSmtp() {
        when(recipients.findRecipient(15L, 5L)).thenThrow(new ResourceAccessException("Auth offline"));

        worker.deliverPendingEmails();

        verify(deliveries).finishDelivery(42L, "PENDING");
        verifyNoInteractions(emails);
    }

    @Test
    void smtpFailureIsUncertainAndDoesNotReturnToPending() {
        doThrow(new MailSendException("SMTP connection dropped"))
                .when(emails).sendCreditRequestEmail("requester@example.test", event);

        worker.deliverPendingEmails();

        verify(deliveries).finishDelivery(42L, "UNCERTAIN");
        verify(deliveries, never()).finishDelivery(42L, "PENDING");
        verify(deliveries, never()).finishDelivery(42L, "SENT");
    }

    @Test
    void databaseFailureAfterAcceptanceDoesNotReturnToPending() {
        doThrow(new IllegalStateException("Database unavailable")).when(deliveries).finishDelivery(42L, "SENT");

        assertThatThrownBy(worker::deliverPendingEmails).isInstanceOf(IllegalStateException.class);

        verify(emails).sendCreditRequestEmail("requester@example.test", event);
        verify(deliveries, never()).finishDelivery(42L, "PENDING");
    }
}
