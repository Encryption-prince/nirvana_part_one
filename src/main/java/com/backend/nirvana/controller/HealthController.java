package com.backend.nirvana.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simple health check controller to handle root path requests
 * and provide basic application status information.
 */
@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
            "service", "Brain-to-Music Streaming API",
            "status", "running",
            "websocket_endpoint", "/ws/brain-waves",
            "description", "Connect via WebSocket to stream EEG data and receive generated music"
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy");
    }
}