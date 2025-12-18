package com.backend.nirvana.model;

import lombok.Data;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class SessionData {
    private final String sessionId;
    private final ConcurrentLinkedDeque<Float> signalBuffer;
    private volatile long lastGenerationTime;
    private volatile long lastActivityTime;
    private final AtomicBoolean hasActivePythonRequest;
    
    public SessionData(String sessionId) {
        this.sessionId = sessionId;
        this.signalBuffer = new ConcurrentLinkedDeque<>();
        this.lastGenerationTime = 0L;
        this.lastActivityTime = System.currentTimeMillis();
        this.hasActivePythonRequest = new AtomicBoolean(false);
    }
    
    /**
     * Updates the last activity time to current timestamp
     */
    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }
    
    /**
     * Adds signal data to the buffer and enforces size limit
     * @param signalData List of signal values to add
     */
    public void addSignalData(java.util.List<Float> signalData) {
        for (Float value : signalData) {
            this.signalBuffer.addLast(value);
        }
        
        // Enforce buffer size limit of 2000 samples with FIFO removal
        while (this.signalBuffer.size() > 2000) {
            this.signalBuffer.removeFirst();
        }
        
        updateActivity();
    }
    
    /**
     * Extracts the most recent N samples from the buffer
     * @param count Number of samples to extract
     * @return List of the most recent samples, or empty list if insufficient data
     */
    public java.util.List<Float> extractRecentSamples(int count) {
        if (signalBuffer.size() < count) {
            return java.util.Collections.emptyList();
        }
        
        java.util.List<Float> samples = new java.util.ArrayList<>();
        java.util.Iterator<Float> iterator = signalBuffer.descendingIterator();
        
        for (int i = 0; i < count && iterator.hasNext(); i++) {
            samples.add(0, iterator.next()); // Add to front to maintain chronological order
        }
        
        return samples;
    }
    
    /**
     * Checks if enough time has passed since last generation (30 seconds)
     * @return true if cooldown period has elapsed
     */
    public boolean canGenerateMusic() {
        return (System.currentTimeMillis() - lastGenerationTime) >= 30000; // 30 seconds
    }
    
    /**
     * Updates the last generation time to current timestamp
     */
    public void updateGenerationTime() {
        this.lastGenerationTime = System.currentTimeMillis();
    }
}