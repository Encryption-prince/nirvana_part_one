package com.backend.nirvana.client;

import com.backend.nirvana.dto.MusicResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.FloatRange;
import net.jqwik.api.constraints.Size;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for PythonMLClient request format and timeout behavior.
 */
class PythonMLClientPropertyTest {
    
    /**
     * **Feature: brain-music-streaming, Property 7: Python service request format and timeout**
     * **Validates: Requirements 5.1, 5.2, 5.3**
     * 
     * For any valid signal data, the system should POST correctly formatted JSON 
     * to the Python service with a 30-second timeout.
     */
    @Property(tries = 100)
    void pythonServiceRequestFormatAndTimeout(
            @ForAll @Size(min = 1, max = 2000) List<@FloatRange(min = -1000.0f, max = 1000.0f) Float> signalData) 
            throws Exception {
        
        // Setup mock server for this test
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            
            // Create WebClient pointing to mock server
            WebClient webClient = WebClient.builder()
                    .baseUrl(mockWebServer.url("/").toString())
                    .build();
            
            PythonMLClient pythonMLClient = new PythonMLClient(webClient, objectMapper);
            
            // Arrange: Mock successful response
            mockWebServer.enqueue(new MockResponse()
                    .setBody("{\"audio_base64\": \"test_audio_data\"}")
                    .setHeader("Content-Type", "application/json"));
            
            // Act: Make prediction request
            MusicResponse response = pythonMLClient.predict(signalData);
            
            // Assert: Verify request format
            RecordedRequest recordedRequest = mockWebServer.takeRequest(10, TimeUnit.SECONDS);
            if (recordedRequest == null) {
                throw new AssertionError("No request was received by mock server. Response was: " + response);
            }
            
            // Verify HTTP method and endpoint
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/predict");
            
            // Verify Content-Type header
            assertThat(recordedRequest.getHeader("Content-Type")).contains("application/json");
            
            // Verify request body format
            String requestBody = recordedRequest.getBody().readUtf8();
            assertThat(requestBody).isNotEmpty();
            
            // Parse and verify JSON structure
            Map<String, Object> requestJson = objectMapper.readValue(requestBody, Map.class);
            assertThat(requestJson).containsKey("signal");
            
            // Verify signal field contains the correct data
            @SuppressWarnings("unchecked")
            List<Number> signalField = (List<Number>) requestJson.get("signal");
            assertThat(signalField).hasSize(signalData.size());
            
            // Verify each signal value matches (allowing for floating point precision)
            for (int i = 0; i < signalData.size(); i++) {
                assertThat(signalField.get(i).floatValue())
                        .isCloseTo(signalData.get(i), org.assertj.core.data.Offset.offset(0.0001f));
            }
            
            // Verify response was processed successfully
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("success");
            assertThat(response.getAudioBase64()).isEqualTo("test_audio_data");
            
        } finally {
            mockWebServer.shutdown();
        }
    }
    
    /**
     * Property test for timeout behavior - verifies that requests timeout after 30 seconds.
     */
    @Property(tries = 5) // Fewer tries since this involves timeouts
    void pythonServiceTimeoutHandling(
            @ForAll @Size(min = 1, max = 10) List<@FloatRange(min = -10.0f, max = 10.0f) Float> signalData) 
            throws Exception {
        
        // Setup mock server for this test
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            
            // Create WebClient pointing to mock server
            WebClient webClient = WebClient.builder()
                    .baseUrl(mockWebServer.url("/").toString())
                    .build();
            
            PythonMLClient pythonMLClient = new PythonMLClient(webClient, objectMapper);
            
            // Arrange: Mock server with delayed response (longer than 30 seconds)
            mockWebServer.enqueue(new MockResponse()
                    .setBody("{\"audio_base64\": \"test_audio_data\"}")
                    .setBodyDelay(32, TimeUnit.SECONDS)); // Delay longer than timeout
            
            // Act: Make prediction request
            long startTime = System.currentTimeMillis();
            MusicResponse response = pythonMLClient.predict(signalData);
            long endTime = System.currentTimeMillis();
            
            // Assert: Verify timeout behavior
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("error");
            assertThat(response.getMessage()).contains("Unexpected error calling Python ML service");
            
            // Verify request completed within reasonable time (should timeout around 30 seconds)
            long duration = endTime - startTime;
            assertThat(duration).isLessThan(32000L); // Should timeout before 32 seconds
            assertThat(duration).isGreaterThan(28000L); // Should take at least close to 30 seconds
            
        } finally {
            mockWebServer.shutdown();
        }
    }
    
    /**
     * **Feature: brain-music-streaming, Property 8: Response parsing and error handling**
     * **Validates: Requirements 5.4, 5.5**
     * 
     * For any Python service response (success or failure), the system should parse it correctly 
     * or handle errors gracefully without crashing.
     */
    @Property(tries = 100)
    void responseParsingAndErrorHandling(
            @ForAll @Size(min = 1, max = 100) List<@FloatRange(min = -100.0f, max = 100.0f) Float> signalData,
            @ForAll("responseScenarios") ResponseScenario scenario) 
            throws Exception {
        
        // Setup mock server for this test
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            
            // Create WebClient pointing to mock server
            WebClient webClient = WebClient.builder()
                    .baseUrl(mockWebServer.url("/").toString())
                    .build();
            
            PythonMLClient pythonMLClient = new PythonMLClient(webClient, objectMapper);
            
            // Arrange: Mock response based on scenario
            MockResponse mockResponse = scenario.createMockResponse();
            mockWebServer.enqueue(mockResponse);
            
            // Act: Make prediction request
            MusicResponse response = pythonMLClient.predict(signalData);
            
            // Assert: Verify response handling based on scenario
            assertThat(response).isNotNull();
            
            if (scenario.shouldSucceed()) {
                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getAudioBase64()).isNotNull();
                assertThat(response.getAudioBase64()).isEqualTo(scenario.getExpectedAudio());
                assertThat(response.getMessage()).contains("successfully");
            } else {
                assertThat(response.getStatus()).isEqualTo("error");
                assertThat(response.getAudioBase64()).isNull();
                assertThat(response.getMessage()).isNotNull();
                assertThat(response.getMessage()).isNotEmpty();
            }
            
            // Property: System should never crash regardless of response format
            // (If we reach this point, the system handled the response gracefully)
            
        } finally {
            mockWebServer.shutdown();
        }
    }
    
    @Provide
    Arbitrary<ResponseScenario> responseScenarios() {
        return Arbitraries.oneOf(
            // Valid responses
            Arbitraries.just(new ResponseScenario(
                "{\"audio_base64\": \"valid_audio_data\"}", 
                200, true, "valid_audio_data")),
            Arbitraries.just(new ResponseScenario(
                "{\"audio_base64\": \"another_valid_audio\"}", 
                200, true, "another_valid_audio")),
            
            // Invalid JSON responses
            Arbitraries.just(new ResponseScenario(
                "invalid json", 200, false, null)),
            Arbitraries.just(new ResponseScenario(
                "{\"wrong_field\": \"value\"}", 200, false, null)),
            Arbitraries.just(new ResponseScenario(
                "{\"audio_base64\": null}", 200, false, null)),
            Arbitraries.just(new ResponseScenario(
                "{\"audio_base64\": \"\"}", 200, false, null)),
            Arbitraries.just(new ResponseScenario(
                "", 200, false, null)),
            
            // HTTP error responses
            Arbitraries.just(new ResponseScenario(
                "Internal Server Error", 500, false, null)),
            Arbitraries.just(new ResponseScenario(
                "Bad Request", 400, false, null)),
            Arbitraries.just(new ResponseScenario(
                "Service Unavailable", 503, false, null))
        );
    }
    
    private static class ResponseScenario {
        private final String responseBody;
        private final int statusCode;
        private final boolean shouldSucceed;
        private final String expectedAudio;
        
        public ResponseScenario(String responseBody, int statusCode, boolean shouldSucceed, String expectedAudio) {
            this.responseBody = responseBody;
            this.statusCode = statusCode;
            this.shouldSucceed = shouldSucceed;
            this.expectedAudio = expectedAudio;
        }
        
        public MockResponse createMockResponse() {
            MockResponse response = new MockResponse()
                    .setResponseCode(statusCode)
                    .setBody(responseBody);
            
            if (statusCode == 200) {
                response.setHeader("Content-Type", "application/json");
            }
            
            return response;
        }
        
        public boolean shouldSucceed() {
            return shouldSucceed;
        }
        
        public String getExpectedAudio() {
            return expectedAudio;
        }
    }
}