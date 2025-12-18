package com.backend.nirvana.service;

import com.backend.nirvana.client.PythonMLClient;
import com.backend.nirvana.dto.EEGDataPacket;
import com.backend.nirvana.dto.MusicResponse;
import com.backend.nirvana.model.SessionData;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property tests for BrainService business logic
 * 
 * **Feature: brain-music-streaming, Property 11: Idle session cleanup**
 * **Validates: Requirements 7.4**
 */
class BrainServicePropertyTest {

    /**
     * **Feature: brain-music-streaming, Property 5: Buffer size limit enforcement with FIFO behavior**
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    void bufferSizeLimitEnforcementWithFIFOBehavior(
            @ForAll @NotBlank String sessionId,
            @ForAll @Size(min = 1, max = 50) List<@Size(min = 1, max = 100) List<Float>> signalBatches) {
        
        SessionManager sessionManager = new SessionManager();
        PythonMLClient mockPythonMLClient = Mockito.mock(PythonMLClient.class);
        BrainService brainService = new BrainService(sessionManager, mockPythonMLClient);
        
        // Create session
        sessionManager.createSession(sessionId);
        SessionData session = sessionManager.getSession(sessionId);
        
        // Track all signal data added for FIFO verification
        List<Float> allSignalData = new ArrayList<>();
        
        // Process each batch of signal data
        for (List<Float> signalBatch : signalBatches) {
            EEGDataPacket packet = new EEGDataPacket(System.currentTimeMillis(), signalBatch);
            brainService.processEEGData(sessionId, packet);
            allSignalData.addAll(signalBatch);
        }
        
        // Property: Buffer should never exceed 2000 samples
        assertThat(session.getSignalBuffer().size()).isLessThanOrEqualTo(2000);
        
        // Property: If total data exceeds 2000, buffer should contain the most recent 2000 samples
        if (allSignalData.size() > 2000) {
            assertThat(session.getSignalBuffer().size()).isEqualTo(2000);
            
            // Verify FIFO behavior - buffer should contain the last 2000 samples
            List<Float> expectedLastSamples = allSignalData.subList(
                allSignalData.size() - 2000, allSignalData.size());
            
            List<Float> actualBufferContents = new ArrayList<>(session.getSignalBuffer());
            assertThat(actualBufferContents).isEqualTo(expectedLastSamples);
        } else {
            // If total data is <= 2000, buffer should contain all data
            assertThat(session.getSignalBuffer().size()).isEqualTo(allSignalData.size());
            
            List<Float> actualBufferContents = new ArrayList<>(session.getSignalBuffer());
            assertThat(actualBufferContents).isEqualTo(allSignalData);
        }
    }

    /**
     * **Feature: brain-music-streaming, Property 6: Music generation trigger conditions**
     * **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**
     */
    @Property(tries = 100)
    void musicGenerationTriggerConditions(
            @ForAll @NotBlank String sessionId,
            @ForAll @IntRange(min = 0, max = 3000) int bufferSize,
            @ForAll @IntRange(min = 0, max = 60000) long timeSinceLastGeneration) {
        
        SessionManager sessionManager = new SessionManager();
        PythonMLClient mockPythonMLClient = Mockito.mock(PythonMLClient.class);
        when(mockPythonMLClient.predict(any())).thenReturn(MusicResponse.success("test_audio"));
        BrainService brainService = new BrainService(sessionManager, mockPythonMLClient);
        
        // Create session
        sessionManager.createSession(sessionId);
        SessionData session = sessionManager.getSession(sessionId);
        
        // Set up the session state based on time since last generation
        if (timeSinceLastGeneration < 30000) {
            // Set last generation time to simulate recent generation (within 30 seconds)
            long recentTime = System.currentTimeMillis() - timeSinceLastGeneration;
            session.setLastGenerationTime(recentTime);
        } else {
            // Set last generation time to simulate old generation (more than 30 seconds ago)
            long oldTime = System.currentTimeMillis() - timeSinceLastGeneration;
            session.setLastGenerationTime(oldTime);
        }
        
        // Add signal data to reach desired buffer size
        if (bufferSize > 0) {
            List<Float> signalData = IntStream.range(0, bufferSize)
                    .mapToObj(i -> (float) i)
                    .toList();
            EEGDataPacket packet = new EEGDataPacket(System.currentTimeMillis(), signalData);
            brainService.processEEGData(sessionId, packet);
        }
        
        // Process one more small packet to trigger generation check
        EEGDataPacket triggerPacket = new EEGDataPacket(System.currentTimeMillis(), List.of(1.0f));
        MusicResponse response = brainService.processEEGData(sessionId, triggerPacket);
        
        // Property: Music generation should only be triggered when both conditions are met
        boolean hasEnoughSamples = session.getSignalBuffer().size() >= 1280;
        boolean cooldownElapsed = session.canGenerateMusic();
        boolean shouldGenerate = hasEnoughSamples && cooldownElapsed;
        
        if (shouldGenerate) {
            // Property: Response should be generated when conditions are met
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("success");
            
            // Property: Generation timestamp should be updated
            assertThat(session.getLastGenerationTime()).isGreaterThan(0L);
            
            // Property: Recent generation should prevent immediate re-generation
            assertThat(session.canGenerateMusic()).isFalse();
        } else {
            // Property: No response should be generated when conditions are not met
            assertThat(response).isNull();
        }
        
        // Property: Buffer should still respect size limits regardless of generation
        assertThat(session.getSignalBuffer().size()).isLessThanOrEqualTo(2000);
    }
    
    @Property(tries = 100)
    void sessionCleanupShouldWorkCorrectly(@ForAll @NotBlank String sessionId) {
        SessionManager sessionManager = new SessionManager();
        PythonMLClient mockPythonMLClient = Mockito.mock(PythonMLClient.class);
        BrainService brainService = new BrainService(sessionManager, mockPythonMLClient);
        
        // Create session and add some data
        sessionManager.createSession(sessionId);
        SessionData session = sessionManager.getSession(sessionId);
        session.getHasActivePythonRequest().set(true);
        
        // Property: Session should exist before cleanup
        assertThat(sessionManager.sessionExists(sessionId)).isTrue();
        assertThat(session.getHasActivePythonRequest().get()).isTrue();
        
        // Cleanup session
        brainService.cleanupSession(sessionId);
        
        // Property: Session should be removed after cleanup
        assertThat(sessionManager.sessionExists(sessionId)).isFalse();
        assertThat(sessionManager.getSession(sessionId)).isNull();
    }
    
    /**
     * **Feature: brain-music-streaming, Property 11: Idle session cleanup**
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Idle session cleanup")
    void idleSessionCleanup(
            @ForAll @NotBlank String sessionId,
            @ForAll @IntRange(min = 0, max = 300000) long idleTimeMillis) {
        
        SessionManager sessionManager = new SessionManager();
        PythonMLClient mockPythonMLClient = Mockito.mock(PythonMLClient.class);
        BrainService brainService = new BrainService(sessionManager, mockPythonMLClient);
        
        // Create session
        sessionManager.createSession(sessionId);
        SessionData session = sessionManager.getSession(sessionId);
        assertThat(sessionManager.sessionExists(sessionId)).isTrue();
        
        // Add some data to the session to make it more realistic
        session.addSignalData(List.of(1.0f, 2.0f, 3.0f));
        session.getHasActivePythonRequest().set(Math.random() > 0.5); // Randomly set active request
        
        // Set the session's last activity time based on the idle time parameter
        // This must be done AFTER addSignalData() since that method calls updateActivity()
        long currentTime = System.currentTimeMillis();
        long lastActivityTime = currentTime - idleTimeMillis;
        session.setLastActivityTime(lastActivityTime);
        
        // Record initial state
        int initialBufferSize = session.getSignalBuffer().size();
        boolean initialPythonRequestState = session.getHasActivePythonRequest().get();
        
        // Run the scheduled cleanup
        brainService.scheduleStaleSessionCleanup();
        
        // Property: Sessions idle for more than 5 minutes (300000ms) should be cleaned up
        boolean shouldBeCleanedUp = idleTimeMillis > 300000;
        
        if (shouldBeCleanedUp) {
            // Property: Idle session should be removed
            assertThat(sessionManager.sessionExists(sessionId))
                    .as("Session idle for %d ms should be cleaned up", idleTimeMillis)
                    .isFalse();
            
            // Property: Session data should be null after cleanup
            assertThat(sessionManager.getSession(sessionId))
                    .as("Session data should be null after cleanup")
                    .isNull();
        } else {
            // Property: Active session should remain
            assertThat(sessionManager.sessionExists(sessionId))
                    .as("Session idle for %d ms should not be cleaned up", idleTimeMillis)
                    .isTrue();
            
            // Property: Session data should still be accessible
            SessionData remainingSession = sessionManager.getSession(sessionId);
            assertThat(remainingSession)
                    .as("Session data should still exist for active session")
                    .isNotNull();
            
            // Property: Session data should be preserved for active sessions
            assertThat(remainingSession.getSignalBuffer().size())
                    .as("Buffer size should be preserved for active session")
                    .isEqualTo(initialBufferSize);
        }
        
        // Property: System should remain stable after cleanup
        // Create a new session to verify system stability
        String newSessionId = sessionId + "-new";
        sessionManager.createSession(newSessionId);
        assertThat(sessionManager.sessionExists(newSessionId))
                .as("New sessions should work normally after cleanup")
                .isTrue();
    }
}