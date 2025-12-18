package com.backend.nirvana.integration;

import com.backend.nirvana.service.SessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic integration test to verify Spring Boot context loads correctly
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BasicIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionManager sessionManager;

    @Test
    void contextLoads() {
        assertThat(port).isGreaterThan(0);
        assertThat(sessionManager).isNotNull();
    }

    @Test
    void sessionManagerWorks() {
        String testSessionId = "test-session-123";
        
        // Create session
        sessionManager.createSession(testSessionId);
        assertThat(sessionManager.sessionExists(testSessionId)).isTrue();
        
        // Remove session
        sessionManager.removeSession(testSessionId);
        assertThat(sessionManager.sessionExists(testSessionId)).isFalse();
    }
}