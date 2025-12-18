package com.backend.nirvana.service;

import com.backend.nirvana.model.SessionData;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for SessionManager thread safety and session management
 */
class SessionManagerPropertyTest {

    /**
     * **Feature: brain-music-streaming, Property 1: Session initialization creates correct state**
     * **Validates: Requirements 1.1, 1.3**
     */
    @Property(tries = 100)
    void sessionInitializationCreatesCorrectState(@ForAll @NotBlank String sessionId) {
        SessionManager sessionManager = new SessionManager();
        
        // Create a new session
        SessionData sessionData = sessionManager.createSession(sessionId);
        
        // Property: Session should be created with correct initial state
        assertThat(sessionData).isNotNull();
        assertThat(sessionData.getSessionId()).isEqualTo(sessionId);
        assertThat(sessionData.getSignalBuffer()).isNotNull();
        assertThat(sessionData.getSignalBuffer()).isEmpty();
        assertThat(sessionData.getLastGenerationTime()).isEqualTo(0L);
        assertThat(sessionData.getLastActivityTime()).isGreaterThan(0L);
        assertThat(sessionData.getHasActivePythonRequest()).isNotNull();
        assertThat(sessionData.getHasActivePythonRequest().get()).isFalse();
        
        // Property: Session should be retrievable by ID
        SessionData retrievedSession = sessionManager.getSession(sessionId);
        assertThat(retrievedSession).isEqualTo(sessionData);
        assertThat(retrievedSession.getSessionId()).isEqualTo(sessionId);
        
        // Property: Session should appear in active sessions list
        List<String> activeSessions = sessionManager.getActiveSessions();
        assertThat(activeSessions).contains(sessionId);
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
        assertThat(sessionManager.sessionExists(sessionId)).isTrue();
    }

    /**
     * **Feature: brain-music-streaming, Property 3: Session isolation under concurrent access**
     * **Validates: Requirements 1.5, 3.4, 3.5, 8.1**
     */
    @Property(tries = 100)
    void sessionIsolationUnderConcurrentAccess(@ForAll List<@NotBlank String> sessionIds) {
        Assume.that(sessionIds.size() >= 2 && sessionIds.size() <= 10);
        Assume.that(sessionIds.stream().distinct().count() == sessionIds.size()); // All unique
        
        SessionManager sessionManager = new SessionManager();
        ExecutorService executor = Executors.newFixedThreadPool(sessionIds.size());
        
        try {
            // Create sessions concurrently
            List<CompletableFuture<SessionData>> creationFutures = sessionIds.stream()
                    .map(sessionId -> CompletableFuture.supplyAsync(() -> 
                            sessionManager.createSession(sessionId), executor))
                    .toList();
            
            // Wait for all sessions to be created
            List<SessionData> createdSessions = creationFutures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            // Property: All sessions should be created successfully
            assertThat(createdSessions).hasSize(sessionIds.size());
            assertThat(createdSessions).allMatch(session -> session != null);
            
            // Property: Each session should have correct isolation
            for (int i = 0; i < sessionIds.size(); i++) {
                String sessionId = sessionIds.get(i);
                SessionData sessionData = createdSessions.get(i);
                
                assertThat(sessionData.getSessionId()).isEqualTo(sessionId);
                assertThat(sessionData.getSignalBuffer()).isEmpty();
                assertThat(sessionData.getLastGenerationTime()).isEqualTo(0L);
            }
            
            // Property: Concurrent operations on different sessions should not interfere
            List<CompletableFuture<Void>> operationFutures = IntStream.range(0, sessionIds.size())
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        String sessionId = sessionIds.get(i);
                        SessionData session = sessionManager.getSession(sessionId);
                        
                        // Perform operations specific to this session
                        session.addSignalData(List.of(1.0f * i, 2.0f * i, 3.0f * i));
                        session.updateActivity();
                        session.updateGenerationTime();
                    }, executor))
                    .toList();
            
            // Wait for all operations to complete
            operationFutures.forEach(CompletableFuture::join);
            
            // Property: Each session should maintain its own state
            for (int i = 0; i < sessionIds.size(); i++) {
                String sessionId = sessionIds.get(i);
                SessionData session = sessionManager.getSession(sessionId);
                
                assertThat(session.getSignalBuffer()).hasSize(3);
                assertThat(session.getSignalBuffer()).containsExactly(1.0f * i, 2.0f * i, 3.0f * i);
                assertThat(session.getLastGenerationTime()).isGreaterThan(0L);
            }
            
            // Property: Total session count should match created sessions
            assertThat(sessionManager.getActiveSessionCount()).isEqualTo(sessionIds.size());
            assertThat(sessionManager.getActiveSessions()).containsExactlyInAnyOrderElementsOf(sessionIds);
            
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Property(tries = 100)
    void sessionRemovalShouldWorkCorrectly(@ForAll @NotBlank String sessionId) {
        SessionManager sessionManager = new SessionManager();
        
        // Create and then remove session
        SessionData originalSession = sessionManager.createSession(sessionId);
        assertThat(sessionManager.sessionExists(sessionId)).isTrue();
        
        SessionData removedSession = sessionManager.removeSession(sessionId);
        
        // Property: Removed session should match original
        assertThat(removedSession).isEqualTo(originalSession);
        
        // Property: Session should no longer exist
        assertThat(sessionManager.sessionExists(sessionId)).isFalse();
        assertThat(sessionManager.getSession(sessionId)).isNull();
        assertThat(sessionManager.getActiveSessions()).doesNotContain(sessionId);
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
        
        // Property: Removing non-existent session should return null
        SessionData nonExistentRemoval = sessionManager.removeSession(sessionId);
        assertThat(nonExistentRemoval).isNull();
    }
    
    @Property(tries = 100)
    void idleSessionCleanupShouldWorkCorrectly(@ForAll @NotBlank String sessionId) throws InterruptedException {
        SessionManager sessionManager = new SessionManager();
        
        // Create session
        SessionData session = sessionManager.createSession(sessionId);
        assertThat(sessionManager.sessionExists(sessionId)).isTrue();
        
        // Property: Fresh session should not be cleaned up
        List<String> removedSessions = sessionManager.removeIdleSessions(60000); // 1 minute
        assertThat(removedSessions).isEmpty();
        assertThat(sessionManager.sessionExists(sessionId)).isTrue();
        
        // Wait a small amount to ensure time has passed
        Thread.sleep(10);
        
        // Property: Session with very short timeout should be cleaned up
        List<String> removedAfterTimeout = sessionManager.removeIdleSessions(5); // 5ms timeout
        assertThat(removedAfterTimeout).contains(sessionId);
        assertThat(sessionManager.sessionExists(sessionId)).isFalse();
    }
}