package com.backend.nirvana.handler;

import com.backend.nirvana.dto.EEGDataPacket;
import com.backend.nirvana.dto.MusicResponse;
import com.backend.nirvana.service.BrainService;
import com.backend.nirvana.service.SessionManager;
import com.backend.nirvana.service.ResourceConstraintHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for processing EEG data streams from frontend clients.
 * Manages session lifecycle and delegates EEG data processing to business services.
 */
@Component
public class BrainWaveHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(BrainWaveHandler.class);
    
    private final SessionManager sessionManager;
    private final BrainService brainService;
    private final ObjectMapper objectMapper;
    private final ResourceConstraintHandler resourceConstraintHandler;

    @Autowired
    public BrainWaveHandler(SessionManager sessionManager, BrainService brainService, 
                           ObjectMapper objectMapper, 
                           @Autowired(required = false) ResourceConstraintHandler resourceConstraintHandler) {
        this.sessionManager = sessionManager;
        this.brainService = brainService;
        this.objectMapper = objectMapper;
        this.resourceConstraintHandler = resourceConstraintHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        logger.info("WebSocket connection established for session: {}", sessionId);
        
        // Check resource constraints before accepting new session (if handler is available)
        if (resourceConstraintHandler != null && !resourceConstraintHandler.canAcceptNewSession()) {
            logger.warn("Rejecting connection for session {} due to resource constraints", sessionId);
            session.close();
            return;
        }
        
        // Initialize session data for new connection
        sessionManager.createSession(sessionId);
        
        if (resourceConstraintHandler != null) {
            logger.info("Session {} accepted. {}", sessionId, 
                       resourceConstraintHandler.getResourceUtilizationSummary());
        } else {
            logger.info("Session {} accepted.", sessionId);
        }
        
        super.afterConnectionEstablished(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        
        logger.debug("Received message from session {}: {}", sessionId, payload);
        
        try {
            // Parse incoming EEG data packet
            EEGDataPacket eegData = objectMapper.readValue(payload, EEGDataPacket.class);
            
            // Validate that we have a valid session
            if (!sessionManager.sessionExists(sessionId)) {
                logger.warn("Received data for non-existent session: {}", sessionId);
                sendErrorResponse(session, "Session not found");
                return;
            }
            
            logger.debug("Successfully parsed EEG data for session {}: timestamp={}, signalData.size={}", 
                       sessionId, eegData.getTimestamp(), 
                       eegData.getSignalData() != null ? eegData.getSignalData().size() : "null");
            
            // Process EEG data through BrainService with error isolation
            MusicResponse response = brainService.processEEGData(sessionId, eegData);
            
            // Send response if music was generated, or send acknowledgment for buffered data
            if (response != null) {
                sendMusicResponse(session, response);
            } else {
                // Send acknowledgment that data was received and buffered
                MusicResponse ackResponse = new MusicResponse(null, "buffering", "Data received and buffered");
                sendMusicResponse(session, ackResponse);
            }
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Handle JSON parsing errors specifically
            logger.error("JSON parsing error for session {}: {}", sessionId, e.getMessage());
            sendErrorResponse(session, "Invalid message format");
        } catch (IllegalArgumentException e) {
            // Handle validation errors
            logger.error("Validation error for session {}: {}", sessionId, e.getMessage());
            sendErrorResponse(session, "Invalid data: " + e.getMessage());
        } catch (Exception e) {
            // Catch all other exceptions to ensure error isolation
            // One client's error should not affect other clients
            logger.error("Error processing message from session {} (isolated): {}", 
                        sessionId, e.getMessage(), e);
            sendErrorResponse(session, "Processing error occurred");
            
            // Do not propagate exception - maintain system stability
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        logger.info("WebSocket connection closed for session: {} with status: {}", sessionId, status);
        
        // Clean up session resources through BrainService
        brainService.cleanupSession(sessionId);
        
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        logger.error("Transport error for session {}: {}", sessionId, exception.getMessage(), exception);
        
        // Clean up session on transport error through BrainService
        brainService.cleanupSession(sessionId);
        
        super.handleTransportError(session, exception);
    }

    /**
     * Sends a music response to the client via WebSocket with JSON serialization
     */
    private void sendMusicResponse(WebSocketSession session, MusicResponse response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(responseJson));
            
            if ("success".equals(response.getStatus())) {
                logger.info("Successfully sent music response to session: {}", session.getId());
            } else {
                logger.warn("Sent error response to session {}: {}", session.getId(), response.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to send music response to session {}: {}", session.getId(), e.getMessage(), e);
            // Try to send a simple error response as fallback
            sendErrorResponse(session, "Failed to send response");
        }
    }

    /**
     * Sends an error response to the client via WebSocket
     */
    private void sendErrorResponse(WebSocketSession session, String errorMessage) {
        try {
            MusicResponse errorResponse = MusicResponse.error(errorMessage);
            String responseJson = objectMapper.writeValueAsString(errorResponse);
            session.sendMessage(new TextMessage(responseJson));
            logger.warn("Sent error response to session {}: {}", session.getId(), errorMessage);
        } catch (Exception e) {
            logger.error("Failed to send error response to session {}: {}", session.getId(), e.getMessage(), e);
        }
    }
}