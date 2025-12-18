package com.backend.nirvana.client;

import com.backend.nirvana.dto.MusicResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with the external Python ML service.
 * Handles HTTP communication, request formatting, response parsing, and error handling.
 */
@Component
public class PythonMLClient {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/predict";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    @Autowired
    public PythonMLClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        String baseUrl = System.getProperty("python.ml.service.url", "http://localhost:8000/predict");
        // Extract base URL from full URL
        if (baseUrl.endsWith("/predict")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/predict".length());
        }
        
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
    }
    
    // Constructor for testing with custom base URL
    public PythonMLClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Makes a prediction request to the Python ML service.
     * 
     * @param signalData List of float values representing EEG signal data
     * @return MusicResponse containing the generated audio or error information
     */
    public MusicResponse predict(List<Float> signalData) {
        try {
            // Create request payload with "signal" field containing float array
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("signal", signalData);
            
            // Make HTTP POST request with 30-second timeout
            String responseBody = webClient.post()
                    .uri("/predict")
                    .bodyValue(requestPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
            
            // Parse response and extract audio_base64 field
            return handleResponse(responseBody);
            
        } catch (WebClientRequestException e) {
            // Connection errors, DNS issues, etc.
            return handleError("Failed to connect to Python ML service: " + e.getMessage());
        } catch (WebClientResponseException e) {
            // HTTP error responses (4xx, 5xx)
            return handleError("Python ML service returned error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            // Timeout, parsing errors, or other unexpected issues
            return handleError("Unexpected error calling Python ML service: " + e.getMessage());
        }
    }
    
    /**
     * Parses the JSON response from the Python ML service and extracts the audio data.
     * 
     * @param responseBody The raw JSON response body
     * @return MusicResponse with extracted audio data or error
     */
    private MusicResponse handleResponse(String responseBody) {
        try {
            if (responseBody == null || responseBody.trim().isEmpty()) {
                return MusicResponse.error("Empty response from Python ML service");
            }
            
            JsonNode responseJson = objectMapper.readTree(responseBody);
            
            // Extract the audio_base64 field
            JsonNode audioNode = responseJson.get("audio_base64");
            if (audioNode == null || audioNode.isNull()) {
                return MusicResponse.error("Missing audio_base64 field in Python ML service response");
            }
            
            String audioBase64 = audioNode.asText();
            if (audioBase64 == null || audioBase64.trim().isEmpty()) {
                return MusicResponse.error("Empty audio_base64 field in Python ML service response");
            }
            
            return MusicResponse.success(audioBase64);
            
        } catch (Exception e) {
            return MusicResponse.error("Failed to parse Python ML service response: " + e.getMessage());
        }
    }
    
    /**
     * Converts service errors to appropriate error responses.
     * 
     * @param errorMessage The error message to include in the response
     * @return MusicResponse with error status and message
     */
    private MusicResponse handleError(String errorMessage) {
        return MusicResponse.error(errorMessage);
    }
}