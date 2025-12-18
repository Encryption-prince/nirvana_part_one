package com.backend.nirvana.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles resource constraints and implements system stability measures.
 * Provides centralized resource management and constraint enforcement.
 */
@Service
public class ResourceConstraintHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceConstraintHandler.class);
    
    private final SessionManager sessionManager;
    private final SystemHealthMonitor systemHealthMonitor;
    
    // Resource constraint thresholds
    private static final double MEMORY_PRESSURE_THRESHOLD = 0.75; // 75% memory usage
    private static final int MAX_CONCURRENT_SESSIONS = 150;
    private static final int MAX_BUFFER_SIZE_PER_SESSION = 2000;
    
    @Autowired
    public ResourceConstraintHandler(SessionManager sessionManager, SystemHealthMonitor systemHealthMonitor) {
        this.sessionManager = sessionManager;
        this.systemHealthMonitor = systemHealthMonitor;
    }
    
    /**
     * Checks if the system can accept a new session based on current resource constraints
     * @return true if new session can be accepted, false otherwise
     */
    public boolean canAcceptNewSession() {
        // Check memory pressure
        if (systemHealthMonitor.getMemoryUsageRatio() > MEMORY_PRESSURE_THRESHOLD) {
            logger.warn("Rejecting new session due to memory pressure: {:.1f}%", 
                       systemHealthMonitor.getMemoryUsageRatio() * 100);
            return false;
        }
        
        // Check session count limit
        if (sessionManager.getActiveSessionCount() >= MAX_CONCURRENT_SESSIONS) {
            logger.warn("Rejecting new session due to session limit: {} active sessions", 
                       sessionManager.getActiveSessionCount());
            return false;
        }
        
        return true;
    }
    
    /**
     * Enforces buffer size limits for a specific session
     * @param sessionId The session to check
     * @return true if buffer is within limits, false if action was taken
     */
    public boolean enforceBufferLimits(String sessionId) {
        var session = sessionManager.getSession(sessionId);
        if (session == null) {
            return true; // Session doesn't exist, no action needed
        }
        
        int bufferSize = session.getSignalBuffer().size();
        if (bufferSize > MAX_BUFFER_SIZE_PER_SESSION) {
            logger.warn("Buffer size limit exceeded for session {}: {} samples", 
                       sessionId, bufferSize);
            
            // The SessionData.addSignalData() method already handles FIFO removal
            // This is just for monitoring and logging
            return false;
        }
        
        return true;
    }
    
    /**
     * Handles resource pressure by cleaning up idle sessions and freeing memory
     */
    public void handleResourcePressure() {
        logger.info("Handling resource pressure - current memory usage: {:.1f}%", 
                   systemHealthMonitor.getMemoryUsageRatio() * 100);
        
        // Clean up idle sessions more aggressively
        int initialSessionCount = sessionManager.getActiveSessionCount();
        sessionManager.removeIdleSessions(45000); // 45 seconds instead of 60
        int cleanedSessions = initialSessionCount - sessionManager.getActiveSessionCount();
        
        if (cleanedSessions > 0) {
            logger.info("Cleaned up {} idle sessions due to resource pressure", cleanedSessions);
        }
        
        // Suggest garbage collection
        System.gc();
        
        // Log results
        logger.info("Resource pressure handling completed. Active sessions: {}, Memory usage: {:.1f}%",
                   sessionManager.getActiveSessionCount(),
                   systemHealthMonitor.getMemoryUsageRatio() * 100);
    }
    
    /**
     * Checks if the system is under resource pressure
     * @return true if system is under pressure
     */
    public boolean isUnderResourcePressure() {
        return systemHealthMonitor.getMemoryUsageRatio() > MEMORY_PRESSURE_THRESHOLD ||
               sessionManager.getActiveSessionCount() > MAX_CONCURRENT_SESSIONS * 0.8;
    }
    
    /**
     * Gets current resource utilization summary
     * @return String describing current resource usage
     */
    public String getResourceUtilizationSummary() {
        return String.format("Memory: %.1f%%, Sessions: %d/%d, System Healthy: %s",
                           systemHealthMonitor.getMemoryUsageRatio() * 100,
                           sessionManager.getActiveSessionCount(),
                           MAX_CONCURRENT_SESSIONS,
                           systemHealthMonitor.isSystemHealthy());
    }
}