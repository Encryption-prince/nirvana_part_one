package com.backend.nirvana.config;

import com.backend.nirvana.handler.BrainWaveHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration class that registers WebSocket handlers
 * and configures CORS policy for frontend connections.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BrainWaveHandler brainWaveHandler;

    public WebSocketConfig(BrainWaveHandler brainWaveHandler) {
        this.brainWaveHandler = brainWaveHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register BrainWaveHandler at /ws/brain-waves endpoint
        // Allow all origins to prevent CORS issues for frontend connections
        registry.addHandler(brainWaveHandler, "/ws/brain-waves")
                .setAllowedOrigins("*");
    }
}