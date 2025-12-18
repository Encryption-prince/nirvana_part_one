package com.backend.nirvana.service;

import com.backend.nirvana.model.SessionData;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe session management component that handles client sessions
 * and their associated data buffers using concurrent data structures.
 */
@Service
public class SessionManager {
    
    private final ConcurrentHashMap<String, SessionData> sessions = new ConcurrentHashMap<>();
    
    /**
     * Creates a new session with initialized SessionData
     * @param sessionId Unique identifier for the session
     * @return The created SessionData object
     */
    public SessionData createSession(String sessionId) {
        SessionData sessionData = new SessionData(sessionId);
        sessions.put(sessionId, sessionData);
        return sessionData;
    }
    
    /**
     * Retrieves session data for the given session ID
     * @param sessionId The session identifier
     * @return SessionData if exists, null otherwise
     */
    public SessionData getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Removes a session and cleans up associated resources
     * @param sessionId The session identifier to remove
     * @return The removed SessionData, or null if session didn't exist
     */
    public SessionData removeSession(String sessionId) {
        SessionData sessionData = sessions.remove(sessionId);
        if (sessionData != null) {
            // Clear the signal buffer to free memory
            sessionData.getSignalBuffer().clear();
            // Cancel any active Python requests
            sessionData.getHasActivePythonRequest().set(false);
        }
        return sessionData;
    }
    
    /**
     * Returns a list of all active session IDs
     * @return List of active session identifiers
     */
    public List<String> getActiveSessions() {
        return sessions.keySet().stream().collect(Collectors.toList());
    }
    
    /**
     * Returns the number of active sessions
     * @return Count of active sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * Checks if a session exists
     * @param sessionId The session identifier
     * @return true if session exists, false otherwise
     */
    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
    
    /**
     * Removes sessions that have been idle for more than the specified timeout
     * @param timeoutMillis Timeout in milliseconds
     * @return List of removed session IDs
     */
    public List<String> removeIdleSessions(long timeoutMillis) {
        long currentTime = System.currentTimeMillis();
        return sessions.entrySet().stream()
                .filter(entry -> (currentTime - entry.getValue().getLastActivityTime()) > timeoutMillis)
                .map(entry -> {
                    SessionData sessionData = sessions.remove(entry.getKey());
                    if (sessionData != null) {
                        // Clear the signal buffer to free memory
                        sessionData.getSignalBuffer().clear();
                        // Cancel any active Python requests
                        sessionData.getHasActivePythonRequest().set(false);
                    }
                    return entry.getKey();
                })
                .collect(Collectors.toList());
    }
}