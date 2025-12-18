package com.backend.nirvana.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * System health monitoring service that tracks resource usage,
 * session counts, and system stability metrics.
 */
@Service
public class SystemHealthMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemHealthMonitor.class);
    
    private final SessionManager sessionManager;
    
    // Health metrics
    private volatile long totalMemoryUsage = 0;
    private volatile long freeMemoryAvailable = 0;
    private volatile int activeSessionCount = 0;
    private volatile boolean systemHealthy = true;
    
    // Thresholds for resource constraints
    private static final double MEMORY_WARNING_THRESHOLD = 0.8; // 80% memory usage
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.9; // 90% memory usage
    private static final int MAX_SESSIONS_WARNING = 100;
    private static final int MAX_SESSIONS_CRITICAL = 200;
    
    @Autowired
    public SystemHealthMonitor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    /**
     * Scheduled health check that runs every 30 seconds
     */
    @Scheduled(fixedRate = 30000)
    public void performHealthCheck() {
        try {
            updateSystemMetrics();
            checkResourceConstraints();
            logHealthStatus();
        } catch (Exception e) {
            logger.error("Error during health check: {}", e.getMessage(), e);
            systemHealthy = false;
        }
    }
    
    /**
     * Updates current system metrics
     */
    private void updateSystemMetrics() {
        Runtime runtime = Runtime.getRuntime();
        totalMemoryUsage = runtime.totalMemory() - runtime.freeMemory();
        freeMemoryAvailable = runtime.freeMemory();
        activeSessionCount = sessionManager.getActiveSessionCount();
    }
    
    /**
     * Checks for resource constraints and triggers appropriate actions
     */
    private void checkResourceConstraints() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        double memoryUsageRatio = (double) totalMemoryUsage / maxMemory;
        
        // Check memory constraints
        if (memoryUsageRatio > MEMORY_CRITICAL_THRESHOLD) {
            logger.error("CRITICAL: Memory usage at {:.1f}% - triggering emergency cleanup", 
                        memoryUsageRatio * 100);
            triggerEmergencyCleanup();
            systemHealthy = false;
        } else if (memoryUsageRatio > MEMORY_WARNING_THRESHOLD) {
            logger.warn("WARNING: Memory usage at {:.1f}% - monitoring closely", 
                       memoryUsageRatio * 100);
            systemHealthy = false;
        } else {
            systemHealthy = true;
        }
        
        // Check session count constraints
        if (activeSessionCount > MAX_SESSIONS_CRITICAL) {
            logger.error("CRITICAL: Too many active sessions ({}) - triggering session cleanup", 
                        activeSessionCount);
            triggerSessionCleanup();
            systemHealthy = false;
        } else if (activeSessionCount > MAX_SESSIONS_WARNING) {
            logger.warn("WARNING: High number of active sessions ({})", activeSessionCount);
        }
    }
    
    /**
     * Triggers emergency cleanup when system resources are critically low
     */
    private void triggerEmergencyCleanup() {
        logger.info("Triggering emergency cleanup due to resource constraints");
        
        // Force garbage collection
        System.gc();
        
        // Clean up idle sessions more aggressively (30 seconds instead of 1 minute)
        sessionManager.removeIdleSessions(30000);
        
        // Log cleanup results
        logger.info("Emergency cleanup completed. Active sessions: {}", 
                   sessionManager.getActiveSessionCount());
    }
    
    /**
     * Triggers session cleanup when too many sessions are active
     */
    private void triggerSessionCleanup() {
        logger.info("Triggering session cleanup due to high session count");
        
        // Remove idle sessions with shorter timeout
        sessionManager.removeIdleSessions(45000); // 45 seconds
        
        logger.info("Session cleanup completed. Active sessions: {}", 
                   sessionManager.getActiveSessionCount());
    }
    
    /**
     * Logs current system health status
     */
    private void logHealthStatus() {
        if (logger.isDebugEnabled()) {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            double memoryUsageRatio = (double) totalMemoryUsage / maxMemory;
            
            logger.debug("System Health Status: " +
                        "Memory Usage: {:.1f}% ({} MB / {} MB), " +
                        "Active Sessions: {}, " +
                        "System Healthy: {}",
                        memoryUsageRatio * 100,
                        totalMemoryUsage / (1024 * 1024),
                        maxMemory / (1024 * 1024),
                        activeSessionCount,
                        systemHealthy);
        }
    }
    
    /**
     * Returns current system health status
     */
    public boolean isSystemHealthy() {
        return systemHealthy;
    }
    
    /**
     * Returns current memory usage ratio (0.0 to 1.0)
     */
    public double getMemoryUsageRatio() {
        Runtime runtime = Runtime.getRuntime();
        return (double) totalMemoryUsage / runtime.maxMemory();
    }
    
    /**
     * Returns current active session count
     */
    public int getActiveSessionCount() {
        return activeSessionCount;
    }
}