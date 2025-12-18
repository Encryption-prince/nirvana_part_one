package com.backend.nirvana.handler;

import com.backend.nirvana.dto.EEGDataPacket;
import com.backend.nirvana.dto.MusicResponse;
import com.backend.nirvana.model.SessionData;
import com.backend.nirvana.service.BrainService;
import com.backend.nirvana.service.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * **Feature: brain-music-streaming, Property 2: Malformed input handling preserves system stability**
 * **Validates: Requirements 1.4, 2.5**
 * 
 * **Feature: brain-music-streaming, Property 10: Complete session cleanup on connection close**
 * **Validates: Requirements 7.1, 7.2, 7.3, 7.5**
 */
class BrainWaveHandlerPropertyTest {

    private BrainWaveHandler handler;
    private SessionManager sessionManager;
    private BrainService brainService;
    private ObjectMapper objectMapper;
    private WebSocketSession mockSession;

    @BeforeProperty
    void setUp() {
        sessionManager = new SessionManager();
        brainService = mock(BrainService.class);
        objectMapper = new ObjectMapper();
        handler = new BrainWaveHandler(sessionManager, brainService, objectMapper, null);
        
        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn("test-session-123");
    }

    @Property(tries = 100)
    @Label("Malformed input handling preserves system stability")
    void malformedInputHandlingPreservesSystemStability(
            @ForAll("malformedJsonStrings") String malformedJson) throws Exception {
        
        // Given: A WebSocket session with an established connection
        handler.afterConnectionEstablished(mockSession);
        
        // Capture any messages sent to the session
        AtomicReference<TextMessage> sentMessage = new AtomicReference<>();
        doAnswer(invocation -> {
            sentMessage.set(invocation.getArgument(0));
            return null;
        }).when(mockSession).sendMessage(any(TextMessage.class));
        
        // When: Malformed JSON is sent to the handler
        TextMessage malformedMessage = new TextMessage(malformedJson);
        
        // Then: The system should handle it gracefully without crashing
        try {
            handler.handleTextMessage(mockSession, malformedMessage);
            
            // System should remain stable - session should still exist
            assertThat(sessionManager.sessionExists("test-session-123"))
                    .as("Session should still exist after malformed input")
                    .isTrue();
            
            // For truly malformed JSON (unparseable), an error response should be sent
            // For valid JSON with invalid data, the system may handle it differently
            if (sentMessage.get() != null) {
                // If a response was sent, it should be a valid error response
                String responsePayload = sentMessage.get().getPayload();
                MusicResponse response = objectMapper.readValue(responsePayload, MusicResponse.class);
                
                assertThat(response.getStatus())
                        .as("Response should have error status")
                        .isEqualTo("error");
                
                assertThat(response.getMessage())
                        .as("Response should contain error message")
                        .isNotNull()
                        .contains("Invalid message format");
            }
            
        } catch (Exception e) {
            // If an exception occurs, it should not be a system-crashing exception
            // The handler should catch and handle all parsing errors gracefully
            throw new AssertionError("System should handle malformed input gracefully without throwing exceptions", e);
        }
        
        // Verify that other sessions would not be affected
        // Create another session to ensure system stability
        WebSocketSession anotherSession = mock(WebSocketSession.class);
        when(anotherSession.getId()).thenReturn("another-session-456");
        
        // This should work normally despite the previous malformed input
        handler.afterConnectionEstablished(anotherSession);
        assertThat(sessionManager.sessionExists("another-session-456"))
                .as("New sessions should work normally after malformed input")
                .isTrue();
    }

    @Provide
    Arbitrary<String> malformedJsonStrings() {
        return Arbitraries.oneOf(
                // Invalid JSON syntax - these should definitely cause parsing errors
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(50)
                        .map(s -> "{" + s), // Unclosed braces
                Arbitraries.strings().withCharRange('0', '9').ofMinLength(1).ofMaxLength(30)
                        .map(s -> s + "}"), // Unmatched closing braces
                
                // Completely invalid JSON - these should cause parsing errors
                Arbitraries.just("not json at all"),
                Arbitraries.just(""),
                Arbitraries.just("123"),
                Arbitraries.just("\"just a string\""),
                Arbitraries.just("[]"),
                Arbitraries.just("{"),
                Arbitraries.just("}"),
                Arbitraries.just("{\"incomplete"),
                Arbitraries.just("{\"key\": }"),
                Arbitraries.just("{\"key\": value}"), // unquoted value
                
                // Invalid JSON structure that should cause parsing errors
                Arbitraries.just("{\"timestamp\": \"not-a-number\", \"signalData\": [1.0, 2.0]}"),
                Arbitraries.just("{\"timestamp\": 123, \"signalData\": \"not-an-array\"}"),
                Arbitraries.just("{\"timestamp\": 123, \"signalData\": [1.0, 2.0, \"invalid\"]}"),
                
                // Some valid JSON with validation issues (these may or may not send error responses)
                Arbitraries.just("{\"timestamp\": null, \"signalData\": [1.0]}"),
                Arbitraries.just("{\"timestamp\": 123, \"signalData\": null}"),
                Arbitraries.just("{\"timestamp\": 123}"), // Missing signalData
                Arbitraries.just("{\"signalData\": [1.0, 2.0]}"), // Missing timestamp
                Arbitraries.just("{\"timestamp\": 123, \"signalData\": []}"), // Empty array
                Arbitraries.just("{\"extraField\": \"value\", \"timestamp\": 123, \"signalData\": [1.0]}"),
                
                // Random strings that are definitely not valid JSON
                Arbitraries.strings()
                        .withCharRange((char) 32, (char) 126) // Printable ASCII
                        .ofMinLength(1).ofMaxLength(100)
                        .filter(s -> !isValidJson(s))
        );
    }
    
    /**
     * Helper method to check if a string is valid JSON at all
     */
    private boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper method to filter out valid EEG data packet JSON strings
     */
    private boolean isValidEEGDataPacketJson(String json) {
        try {
            objectMapper.readValue(json, com.backend.nirvana.dto.EEGDataPacket.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Property(tries = 100)
    @Label("Complete session cleanup on connection close")
    void completeSessionCleanupOnConnectionClose(
            @ForAll("sessionIds") String sessionId,
            @ForAll("eegDataList") List<EEGDataPacket> eegDataList,
            @ForAll("closeStatuses") CloseStatus closeStatus) throws Exception {
        
        // Setup: Create a real BrainService for this test to ensure actual cleanup
        SessionManager cleanupSessionManager = new SessionManager();
        BrainService realBrainService = new BrainService(cleanupSessionManager, mock(com.backend.nirvana.client.PythonMLClient.class));
        BrainWaveHandler cleanupHandler = new BrainWaveHandler(cleanupSessionManager, realBrainService, objectMapper, null);
        
        // Given: A WebSocket session with established connection and some data
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        
        // Establish connection and create session
        cleanupHandler.afterConnectionEstablished(session);
        
        // Verify session was created
        assertThat(cleanupSessionManager.sessionExists(sessionId))
                .as("Session should exist after connection establishment")
                .isTrue();
        
        // Add some EEG data to the session to simulate activity
        SessionData sessionData = cleanupSessionManager.getSession(sessionId);
        assertThat(sessionData).isNotNull();
        
        // Simulate adding data and potentially having active Python requests
        for (EEGDataPacket eegData : eegDataList) {
            if (eegData.getSignalData() != null && !eegData.getSignalData().isEmpty()) {
                sessionData.addSignalData(eegData.getSignalData());
            }
        }
        
        // Simulate having an active Python request (randomly)
        boolean hadActivePythonRequest = Math.random() > 0.5;
        if (hadActivePythonRequest) {
            sessionData.getHasActivePythonRequest().set(true);
        }
        
        // Record initial state
        int initialBufferSize = sessionData.getSignalBuffer().size();
        boolean initialPythonRequestState = sessionData.getHasActivePythonRequest().get();
        
        // When: Connection is closed
        cleanupHandler.afterConnectionClosed(session, closeStatus);
        
        // Then: Session should be completely cleaned up
        assertThat(cleanupSessionManager.sessionExists(sessionId))
                .as("Session should not exist after connection close")
                .isFalse();
        
        // Verify that the session was removed from the session manager
        assertThat(cleanupSessionManager.getSession(sessionId))
                .as("Session data should be null after cleanup")
                .isNull();
        
        // Verify system stability - should be able to create new sessions
        String newSessionId = sessionId + "-new";
        WebSocketSession newSession = mock(WebSocketSession.class);
        when(newSession.getId()).thenReturn(newSessionId);
        
        cleanupHandler.afterConnectionEstablished(newSession);
        assertThat(cleanupSessionManager.sessionExists(newSessionId))
                .as("New sessions should work normally after cleanup")
                .isTrue();
    }

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "session-" + s);
    }

    @Provide
    Arbitrary<List<EEGDataPacket>> eegDataList() {
        return eegDataPackets().list().ofMinSize(0).ofMaxSize(10);
    }

    @Provide
    Arbitrary<EEGDataPacket> eegDataPackets() {
        return Combinators.combine(
                Arbitraries.longs().between(1000000000L, 9999999999L), // timestamp
                Arbitraries.floats().between(-100.0f, 100.0f).list().ofMinSize(1).ofMaxSize(50) // signal data
        ).as(EEGDataPacket::new);
    }

    @Provide
    Arbitrary<CloseStatus> closeStatuses() {
        return Arbitraries.of(
                CloseStatus.NORMAL,
                CloseStatus.GOING_AWAY,
                CloseStatus.PROTOCOL_ERROR,
                CloseStatus.NOT_ACCEPTABLE,
                CloseStatus.SERVER_ERROR
        );
    }
}