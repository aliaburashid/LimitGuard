
package com.example.limitguard.service;

import com.example.limitguard.enums.CreditRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CreditRequestEventServiceTest {

    private CreditRequestEventService creditRequestEventService;

    @BeforeEach
    void setUp() {
        // create a new SSE service before each test
        creditRequestEventService = new CreditRequestEventService();
    }

    // get the SSE connections stored for each bank
    // used only for testing which clients receive notifications
    @SuppressWarnings("unchecked")
    private Map<Long, List<SseEmitter>> getEmitters() throws Exception {

        Field field = CreditRequestEventService.class.getDeclaredField("emitters");
        field.setAccessible(true);

        return (Map<Long, List<SseEmitter>>) field.get(creditRequestEventService);
    }

    // test that a bank receives its own credit request status updates
    @Test
    void shouldSendStatusUpdateToSameInstitution() throws Exception {

        // arrange: create an SSE connection for bank 1
        Long institutionId = 1L;
        creditRequestEventService.subscribe(institutionId);

        // get the connection belonging to bank 1
        SseEmitter emitter = spy(getEmitters().get(institutionId).get(0));

        // replace the stored connection with the spy
        getEmitters().get(institutionId).set(0, emitter);

        // act: publish a RESERVED status update for bank 1
        creditRequestEventService.publishStatusUpdate(
                institutionId,
                42L,
                CreditRequestStatus.RESERVED
        );

        // assert: bank 1 received one status notification
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // test that another bank cannot receive credit request updates
    @Test
    void shouldNotSendStatusUpdateToAnotherInstitution() throws Exception {

        // arrange: create SSE connections for bank 1 and bank 2
        creditRequestEventService.subscribe(1L);
        creditRequestEventService.subscribe(2L);

        // get the connections for both banks
        SseEmitter bankOneEmitter = spy(getEmitters().get(1L).get(0));
        SseEmitter bankTwoEmitter = spy(getEmitters().get(2L).get(0));

        // replace the stored connections with spies
        getEmitters().get(1L).set(0, bankOneEmitter);
        getEmitters().get(2L).set(0, bankTwoEmitter);

        // act: publish a USED status update belonging to bank 1
        creditRequestEventService.publishStatusUpdate(
                1L,
                42L,
                CreditRequestStatus.USED
        );

        // assert: bank 1 receives the notification
        verify(bankOneEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));

        // assert: bank 2 does not receive the notification
        verify(bankTwoEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // test that multiple connected users from the same bank receive updates
    @Test
    void shouldSendStatusUpdateToAllUsersInSameInstitution() throws Exception {

        // arrange: create two SSE connections for bank 1
        creditRequestEventService.subscribe(1L);
        creditRequestEventService.subscribe(1L);

        // get both connections
        List<SseEmitter> connections = getEmitters().get(1L);

        SseEmitter firstEmitter = spy(connections.get(0));
        SseEmitter secondEmitter = spy(connections.get(1));

        // replace the stored connections with spies
        connections.set(0, firstEmitter);
        connections.set(1, secondEmitter);

        // act: publish a REJECTED status update for bank 1
        creditRequestEventService.publishStatusUpdate(
                1L,
                42L,
                CreditRequestStatus.REJECTED
        );

        // assert: both users receive the notification
        verify(firstEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // test that publishing an update without connected users does not fail
    @Test
    void shouldHandleInstitutionWithNoSubscribers() {

        // act and assert: no exception should occur
        assertDoesNotThrow(() ->
                creditRequestEventService.publishStatusUpdate(
                        1L,
                        42L,
                        CreditRequestStatus.RESERVED
                )
        );
    }

    // test that a disconnected SSE client is removed from the bank's connections
    @Test
    void shouldRemoveDisconnectedClient() throws Exception {

        // arrange: create an SSE connection for bank 1
        creditRequestEventService.subscribe(1L);

        // get the stored connection
        List<SseEmitter> connections = getEmitters().get(1L);

        // replace it with a spy that simulates a disconnected client
        SseEmitter emitter = spy(connections.get(0));
        connections.set(0, emitter);

        // simulate an error when sending the status update
        doThrow(new IOException("Client disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        // act: publish a CANCELLED status update
        creditRequestEventService.publishStatusUpdate(
                1L,
                42L,
                CreditRequestStatus.CANCELLED
        );

        // assert: the disconnected client is removed
        assertTrue(connections.isEmpty());
    }
}
