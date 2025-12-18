package com.backend.nirvana.service;

import com.backend.nirvana.client.PythonMLClient;
import com.backend.nirvana.dto.EEGDataPacket;
import com.backend.nirvana.dto.MusicResponse;
import com.backend.nirvana.model.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core business logic component that manages EEG data processing and music generation.
 * Handles buffer management, generation trigger logic, and coordinates with external services.
 */
@Service
public class BrainService {
    
    private static final Logger logger = LoggerFactory.getLogger(BrainService.class);
    
    private final SessionManager sessionManager;
    private final PythonMLClient pythonMLClient;
    private final ResourceConstraintHandler resourceConstraintHandler;
    
    @Autowired
    public BrainService(SessionManager sessionManager, PythonMLClient pythonMLClient, 
                       ResourceConstraintHandler resourceConstraintHandler) {
        this.sessionManager = sessionManager;
        this.pythonMLClient = pythonMLClient;
        this.resourceConstraintHandler = resourceConstraintHandler;
    }
    
    // Constructor for testing without ResourceConstraintHandler
    public BrainService(SessionManager sessionManager, PythonMLClient pythonMLClient) {
        this.sessionManager = sessionManager;
        this.pythonMLClient = pythonMLClient;
        this.resourceConstraintHandler = null; // Will be null in tests
    }
    
    /**
     * Processes incoming EEG data by adding it to the session buffer and checking
     * if music generation should be triggered.
     * Includes resource constraint handling and system stability measures.
     * 
     * @param sessionId The session identifier
     * @param eegData The EEG data packet containing signal data
     * @return MusicResponse if generation is triggered, null if not ready yet
     */
    public MusicResponse processEEGData(String sessionId, EEGDataPacket eegData) {
        SessionData session = sessionManager.getSession(sessionId);
        if (session == null) {
            return MusicResponse.error("Session not found");
        }
        
        // Check for resource pressure before processing (if handler is available)
        if (resourceConstraintHandler != null && resourceConstraintHandler.isUnderResourcePressure()) {
            logger.warn("System under resource pressure - handling constraints");
            resourceConstraintHandler.handleResourcePressure();
        }
        
        // Validate signal data before processing
        if (eegData.getSignalData() == null || eegData.getSignalData().isEmpty()) {
            return MusicResponse.error("Invalid EEG data: signal data is null or empty");
        }
        
        // Add signal data to the session buffer (includes FIFO enforcement)
        session.addSignalData(eegData.getSignalData());
        
        // Enforce buffer limits for this session (if handler is available)
        if (resourceConstraintHandler != null) {
            resourceConstraintHandler.enforceBufferLimits(sessionId);
        }
        
        // Check if music generation should be triggered
        if (shouldTriggerGeneration(session)) {
            return triggerMusicGeneration(session);
        }
        
        return null; // Not ready for generation yet
    }
    
    /**
     * Determines if music generation should be triggered based on buffer size
     * and cooldown period.
     * 
     * @param session The session data to check
     * @return true if generation conditions are met
     */
    private boolean shouldTriggerGeneration(SessionData session) {
        // Check if we have enough samples (1280 or more)
        if (session.getSignalBuffer().size() < 1280) {
            return false;
        }
        
        // Check if cooldown period has elapsed (30 seconds)
        return session.canGenerateMusic();
    }
    
    /**
     * Triggers music generation by extracting samples and calling the ML service.
     * Updates generation timestamp to enforce cooldown.
     * Includes comprehensive error handling for external service failures.
     * 
     * @param session The session data
     * @return MusicResponse with generated music or error
     */
    private MusicResponse triggerMusicGeneration(SessionData session) {
        // Extract exactly 1280 most recent samples
        List<Float> samples = session.extractRecentSamples(1280);
        
        if (samples.isEmpty() || samples.size() != 1280) {
            return MusicResponse.error("Insufficient samples for generation");
        }
        
        // Update generation timestamp to enforce cooldown
        session.updateGenerationTime();
        
        // Set active Python request flag
        session.getHasActivePythonRequest().set(true);
        
        try {
            // Call Python ML service with samples and comprehensive error handling
            MusicResponse response = pythonMLClient.predict(samples);
            
            if (response == null) {
                logger.warn("Python ML service returned null response for session: {}", 
                           session.getSessionId());
                return MusicResponse.error("Music generation service unavailable");
            }
            
            return response;
            
        } catch (RuntimeException e) {
            // Check if it's a connection-related exception
            if (e.getCause() instanceof java.net.ConnectException) {
                logger.error("Connection failed to Python ML service for session {}: {}", 
                            session.getSessionId(), e.getMessage());
                return MusicResponse.error("Music generation service temporarily unavailable");
            }
            
            // Check if it's a timeout-related exception
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
                logger.error("Timeout calling Python ML service for session {}: {}", 
                            session.getSessionId(), e.getMessage());
                return MusicResponse.error("Music generation timed out - please try again");
            }
            
            // Handle other runtime exceptions
            logger.error("Runtime error calling Python ML service for session {}: {}", 
                        session.getSessionId(), e.getMessage(), e);
            return MusicResponse.error("Music generation failed - service error");

            
        } catch (Exception e) {
            // Catch-all for any other exceptions to maintain system stability
            logger.error("Unexpected error during music generation for session {}: {}", 
                        session.getSessionId(), e.getMessage(), e);
            return MusicResponse.error("Music generation failed - unexpected error");
            
        } finally {
            // Always clear active Python request flag to prevent resource leaks
            session.getHasActivePythonRequest().set(false);
        }
    }
    
    /**
     * Cleans up session data and cancels any pending requests.
     * Ensures comprehensive resource cleanup to prevent memory leaks.
     * 
     * @param sessionId The session identifier to clean up
     */
    public void cleanupSession(String sessionId) {
        SessionData session = sessionManager.getSession(sessionId);
        if (session != null) {
            logger.info("Cleaning up session: {}", sessionId);
            
            // Cancel any active Python requests
            session.getHasActivePythonRequest().set(false);
            
            // Clear the signal buffer to free memory immediately
            session.getSignalBuffer().clear();
            
            // Remove the session from the manager (which also does cleanup)
            sessionManager.removeSession(sessionId);
            
            logger.debug("Session cleanup completed for: {}", sessionId);
        }
    }
    
    /**
     * Scheduled task that runs every 60 seconds to clean up stale sessions 
     * that have been idle for more than 5 minutes.
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scheduleStaleSessionCleanup() {
        long fiveMinutesInMillis = 300000L; // 5 minutes instead of 1 minute
        List<String> removedSessions = sessionManager.removeIdleSessions(fiveMinutesInMillis);
        
        // Log cleanup activity if sessions were removed
        if (!removedSessions.isEmpty()) {
            logger.info("Cleaned up {} stale sessions (idle > 5 minutes): {}", removedSessions.size(), removedSessions);
        }
    }
}