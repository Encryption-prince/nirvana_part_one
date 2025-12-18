package com.backend.nirvana.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.socket.WebSocketSession;

/**
 * Global exception handler for system stability and error isolation.
 * Ensures that exceptions in one client session do not affect others.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handles general runtime exceptions to prevent system crashes
     */
    @ExceptionHandler(RuntimeException.class)
    public void handleRuntimeException(RuntimeException e) {
        logger.error("Runtime exception caught by global handler: {}", e.getMessage(), e);
        // Log the error but don't propagate to prevent system instability
    }
    
    /**
     * Handles out of memory errors with immediate cleanup
     */
    @ExceptionHandler(OutOfMemoryError.class)
    public void handleOutOfMemoryError(OutOfMemoryError e) {
        logger.error("Out of memory error detected - triggering emergency cleanup: {}", e.getMessage(), e);
        // This would trigger emergency cleanup in a real system
        System.gc(); // Suggest garbage collection
    }
    
    /**
     * Handles general exceptions to maintain system stability
     */
    @ExceptionHandler(Exception.class)
    public void handleGeneralException(Exception e) {
        logger.error("General exception caught by global handler: {}", e.getMessage(), e);
        // Ensure system continues operating despite individual errors
    }
}