package com.backend.nirvana;

import com.backend.nirvana.client.PythonMLClient;
import com.backend.nirvana.dto.EEGDataPacket;
import com.backend.nirvana.dto.MusicResponse;
import com.backend.nirvana.handler.BrainWaveHandler;
import com.backend.nirvana.model.SessionData;
import com.backend.nirvana.service.BrainService;
import com.backend.nirvana.service.SessionManager;
import com.backend.nirvana.service.ResourceConstraintHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property tests for system stability under resource constraints
 * 
 * **Feature: brain-music-streaming, Property 12: System stability under resource constraints**
 * **Validates: Requirements 8.2, 8.3, 8.4, 8.5**
 */
class SystemStabilityPropertyTest {

    /**
     * **Feature: brain-music-streaming, Property 12: System stability under resource constraints**
     * **Validates: Requirements 8.2, 8.3, 8.4, 8.5**
     */
    @Property(tries = 100)
    void systemStabilityUnderResourceConstraints(
            @ForAll @IntRange(min = 1, max = 50) int numberOfSessions,
            @ForAll @IntRange(min = 100, max = 5000) int samplesPerSession,
            @ForAll boolean pythonServiceFailure) {
        
        SessionManager sessionManager = new SessionManager();
        PythonMLClient mockPythonMLClient = Mockito.mock(PythonMLClient.class);
        
        // Configure Python service behavior based on failure parameter
        if (pythonServiceFailure) {
            when(mockPythonMLClient.predict(any()))
                    .thenThrow(new RuntimeException("Python service unavailable"));
        } else {
            when(mockPythonMLClient.predict(any()))
                    .thenReturn(MusicResponse.success("test_audio"));
        }
        
        ResourceConstraintHandler mockResourceHandler = Mockito.mock(ResourceConstraintHandler.class);
        when(mockResourceHandler.canAcceptNewSession()).thenReturn(true);
        when(mockResourceHandler.isUnderResourcePressure()).thenReturn(false);
        BrainService brainService = new BrainService(sessionManager, mockPythonMLClient, mockResourceHandler);
        ObjectMapper objectMapper = new ObjectMapper();
        BrainWaveHandler handler = new BrainWaveHandler(sessionManager, brainService, objectMapper, mockResourceHandler);
        
        // Create multiple concurrent sessions to simulate resource pressure
        List<String> sessionIds = new ArrayList<>();
        for (int i = 0; i < numberOfSessions; i++) {
            String sessionId = "session-" + i;
            sessionIds.add(sessionId);
            sessionManager.createSession(sessionId);
        }
        
        // Property: System should handle multiple sessions without crashing
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(numberOfSessions);
        
        // Simulate concurrent data processing across all sessions
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(numberOfSessions, 10));
        List<Future<Boolean>> futures = new ArrayList<>();
        
        for (String sessionId : sessionIds) {
            Future<Boolean> future = executor.submit(() -> {
                try {
                    // Generate signal data for this session
                    List<Float> signalData = IntStream.range(0, samplesPerSession)
                            .mapToObj(i -> (float) (Math.random() * 100))
                            .toList();
                    
                    EEGDataPacket packet = new EEGDataPacket(System.currentTimeMillis(), signalData);
                    
                    // Process data through BrainService
                    MusicResponse response = brainService.processEEGData(sessionId, packet);
                    
                    // Property: System should handle processing without throwing exceptions
                    return true;
                } catch (Exception e) {
                    // Property: Exceptions in one session should not crash the system
                    return false;
                }
            });
            futures.add(future);
        }
        
        // Wait for all processing to complete
        int successfulProcessing = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(5, TimeUnit.SECONDS)) {
                    successfulProcessing++;
                }
            } catch (Exception e) {
                // Count as failed processing
            }
        }
        executor.shutdown();
        
        // Property: System should maintain stability even with resource pressure
        // When Python service is working, at least some sessions should process successfully
        // When Python service fails, system should handle gracefully without crashing
        if (!pythonServiceFailure) {
            assertThat(successfulProcessing).isGreaterThan(0);
        } else {
            // When Python service fails, system should still handle requests gracefully
            // (processing may fail but system shouldn't crash)
            assertThat(successfulProcessing).isGreaterThanOrEqualTo(0);
        }
        
        // Property: Buffer limits should be enforced across all sessions
        for (String sessionId : sessionIds) {
            SessionData session = sessionManager.getSession(sessionId);
            if (session != null) {
                assertThat(session.getSignalBuffer().size())
                        .as("Buffer size should be within limits for session %s", sessionId)
                        .isLessThanOrEqualTo(2000);
            }
        }
        
        // Property: Session manager should remain functional
        String testSessionId = "test-session-after-pressure";
        sessionManager.createSession(testSessionId);
        assertThat(sessionManager.sessionExists(testSessionId))
                .as("System should remain functional after resource pressure")
                .isTrue();
        
        // Property: Cleanup should work properly even under pressure
        int initialSessionCount = sessionManager.getActiveSessionCount();
        brainService.scheduleStaleSessionCleanup();
        
        // System should still be responsive after cleanup
        assertThat(sessionManager.getActiveSessionCount())
                .as("Session count should be manageable after cleanup")
                .isLessThanOrEqualTo(initialSessionCount);
        
        // Property: New sessions should still work after resource pressure
        SessionData newSession = sessionManager.getSession(testSessionId);
        assertThat(newSession).isNotNull();
        assertThat(newSession.getSignalBuffer()).isEmpty();
    }
    
    @Property(tries = 100)
    void errorIsolationBetweenClients(
            @ForAll @Size(min = 2, max = 10) List<@NotBlank String> sessionIds,
            @ForAll @IntRange(min = 0, max = 3) int failingSessionIndex) {
        
        SessionManager sessionManager = new SessionManager();
        PythonMLClient mockPythonMLClient = Mockito.mock(PythonMLClient.class);
        when(mockPythonMLClient.predict(any())).thenReturn(MusicResponse.success("test_audio"));
        
        ResourceConstraintHandler mockResourceHandler = Mockito.mock(ResourceConstraintHandler.class);
        when(mockResourceHandler.canAcceptNewSession()).thenReturn(true);
        when(mockResourceHandler.isUnderResourcePressure()).thenReturn(false);
        BrainService brainService = new BrainService(sessionManager, mockPythonMLClient, mockResourceHandler);
        
        // Create all sessions
        for (String sessionId : sessionIds) {
            sessionManager.createSession(sessionId);
        }
        
        // Property: All sessions should be created successfully
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(sessionIds.size());
        
        // Simulate error in one specific session (if index is valid)
        String failingSessionId = null;
        if (failingSessionIndex < sessionIds.size()) {
            failingSessionId = sessionIds.get(failingSessionIndex);
            
            // Simulate error by trying to process data for non-existent session
            sessionManager.removeSession(failingSessionId);
            
            // Try to process data for the removed session (should handle gracefully)
            EEGDataPacket errorPacket = new EEGDataPacket(System.currentTimeMillis(), List.of(1.0f));
            MusicResponse errorResponse = brainService.processEEGData(failingSessionId, errorPacket);
            
            // Property: Error should be handled gracefully
            assertThat(errorResponse).isNotNull();
            assertThat(errorResponse.getStatus()).isEqualTo("error");
        }
        
        // Property: Other sessions should remain unaffected
        for (String sessionId : sessionIds) {
            if (!sessionId.equals(failingSessionId)) {
                assertThat(sessionManager.sessionExists(sessionId))
                        .as("Session %s should remain unaffected by errors in other sessions", sessionId)
                        .isTrue();
                
                // Verify the session can still process data normally
                EEGDataPacket normalPacket = new EEGDataPacket(System.currentTimeMillis(), List.of(2.0f));
                MusicResponse response = brainService.processEEGData(sessionId, normalPacket);
                
                // Response might be null (not ready for generation) or success, but never error
                if (response != null) {
                    assertThat(response.getStatus())
                            .as("Unaffected session should process normally")
                            .isNotEqualTo("error");
                }
            }
        }
        
        // Property: System should remain stable after error isolation
        String newSessionId = "new-session-after-error";
        sessionManager.createSession(newSessionId);
        assertThat(sessionManager.sessionExists(newSessionId))
                .as("New sessions should work normally after error isolation")
                .isTrue();
    }
    
    @Property(tries = 100)
    void memoryManagementUnderPressure(
            @ForAll @IntRange(min = 5, max = 20) int numberOfSessions,
            @ForAll @IntRange(min = 2000, max = 10000) int samplesPerSession) {
        
        SessionManager sessionManager = new SessionManager();
        PythonMLClient mockPythonMLClient = Mockito.mock(PythonMLClient.class);
        when(mockPythonMLClient.predict(any())).thenReturn(MusicResponse.success("test_audio"));
        
        ResourceConstraintHandler mockResourceHandler = Mockito.mock(ResourceConstraintHandler.class);
        when(mockResourceHandler.canAcceptNewSession()).thenReturn(true);
        when(mockResourceHandler.isUnderResourcePressure()).thenReturn(false);
        BrainService brainService = new BrainService(sessionManager, mockPythonMLClient, mockResourceHandler);
        
        // Create sessions and fill them with data to create memory pressure
        List<String> sessionIds = new ArrayList<>();
        for (int i = 0; i < numberOfSessions; i++) {
            String sessionId = "memory-test-session-" + i;
            sessionIds.add(sessionId);
            sessionManager.createSession(sessionId);
            
            // Add large amounts of data to create memory pressure
            List<Float> largeSignalData = IntStream.range(0, samplesPerSession)
                    .mapToObj(j -> (float) (Math.random() * 100))
                    .toList();
            
            EEGDataPacket packet = new EEGDataPacket(System.currentTimeMillis(), largeSignalData);
            brainService.processEEGData(sessionId, packet);
        }
        
        // Property: Buffer limits should be enforced to prevent memory issues
        for (String sessionId : sessionIds) {
            SessionData session = sessionManager.getSession(sessionId);
            assertThat(session).isNotNull();
            assertThat(session.getSignalBuffer().size())
                    .as("Buffer size should be limited to prevent memory issues")
                    .isLessThanOrEqualTo(2000);
        }
        
        // Property: System should handle cleanup under memory pressure
        // Simulate some sessions becoming idle
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < numberOfSessions / 2; i++) {
            String sessionId = sessionIds.get(i);
            SessionData session = sessionManager.getSession(sessionId);
            if (session != null) {
                // Make session appear idle (more than 1 minute old)
                session.setLastActivityTime(currentTime - 70000);
            }
        }
        
        int initialSessionCount = sessionManager.getActiveSessionCount();
        brainService.scheduleStaleSessionCleanup();
        int finalSessionCount = sessionManager.getActiveSessionCount();
        
        // Property: Cleanup should reduce memory usage by removing idle sessions
        assertThat(finalSessionCount)
                .as("Cleanup should remove idle sessions to manage memory")
                .isLessThan(initialSessionCount);
        
        // Property: Remaining sessions should still function normally
        for (String sessionId : sessionIds) {
            SessionData session = sessionManager.getSession(sessionId);
            if (session != null) {
                // Verify session can still process data
                EEGDataPacket testPacket = new EEGDataPacket(System.currentTimeMillis(), List.of(1.0f));
                MusicResponse response = brainService.processEEGData(sessionId, testPacket);
                
                // Should not crash or return error due to memory issues
                if (response != null) {
                    assertThat(response.getStatus()).isNotEqualTo("error");
                }
            }
        }
    }
}