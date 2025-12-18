# Design Document

## Overview

The Brain-to-Music biofeedback system is a real-time Spring Boot application that processes EEG data streams through WebSocket connections and generates music via an external Python ML service. The architecture emphasizes thread safety, resource management, and low-latency processing to provide immediate audio feedback based on brain wave patterns.

The system follows a reactive architecture pattern where EEG data flows through a buffering mechanism, triggers music generation when sufficient data is collected, and streams the results back to clients. Key design principles include thread safety through concurrent data structures, resource cleanup to prevent memory leaks, and graceful error handling to maintain system stability.

## Architecture

The system follows a layered architecture with clear separation of concerns:

**Presentation Layer**: WebSocket endpoints handle client connections and message routing
**Service Layer**: BrainService manages core business logic and coordinates between components  
**Integration Layer**: WebClient handles communication with the external Python ML service
**Data Layer**: In-memory session management with thread-safe concurrent collections

The architecture supports horizontal scaling through stateless service design, with session data isolated per connection. The reactive WebClient enables non-blocking I/O for external service calls, while concurrent data structures ensure thread safety without explicit locking.

## Components and Interfaces

### WebSocketConfig
- Implements WebSocketConfigurer to register WebSocket handlers
- Configures CORS policy to allow all origins
- Registers the BrainWaveHandler at `/ws/brain-waves` endpoint

### BrainWaveHandler extends TextWebSocketHandler
- **handleTextMessage()**: Parses incoming EEG data and delegates to BrainService
- **afterConnectionEstablished()**: Initializes session data for new connections
- **afterConnectionClosed()**: Triggers cleanup of session resources
- **handleTransportError()**: Manages connection errors and cleanup

### BrainService
- **processEEGData(sessionId, eegData)**: Adds data to session buffer and checks generation conditions
- **generateMusic(sessionId, signalData)**: Calls Python ML service and returns Music_Response
- **cleanupSession(sessionId)**: Removes session data and cancels pending requests
- **scheduleStaleSessionCleanup()**: Periodic task to clean up idle sessions

### SessionManager
- **createSession(sessionId)**: Initializes SessionData with empty buffer
- **getSession(sessionId)**: Retrieves session data thread-safely
- **removeSession(sessionId)**: Cleans up session and associated resources
- **getActiveSessions()**: Returns list of active session IDs for monitoring

### PythonMLClient
- **predict(signalData)**: Makes HTTP POST to Python service with timeout
- **handleResponse(response)**: Parses JSON response and extracts audio data
- **handleError(exception)**: Converts service errors to appropriate error responses

## Data Models

### EEGDataPacket
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EEGDataPacket {
    private long timestamp;
    private List<Float> signalData;
}
```

### MusicResponse
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MusicResponse {
    private String audioBase64;
    private String status;
    private String message;
}
```

### SessionData
```java
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
}
```## Corr
ectness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, several properties can be consolidated to eliminate redundancy:

- Buffer management properties (2.4, 4.3, 4.4) can be combined into comprehensive buffer behavior properties
- Thread safety properties (3.1, 3.4, 3.5) can be consolidated into general concurrent access properties  
- Response generation properties (6.1, 6.2, 6.3) can be combined into response format properties
- Cleanup properties (7.1, 7.2, 7.5) can be consolidated into comprehensive cleanup properties
- Error handling properties (5.5, 6.4, 8.1, 8.2) can be combined into general error handling properties

### Core Properties

**Property 1: Session initialization creates correct state**
*For any* WebSocket connection establishment, the system should create a new session with an empty signal buffer and initialized timestamps
**Validates: Requirements 1.1, 1.3**

**Property 2: Malformed input handling preserves system stability**
*For any* malformed JSON input, the system should reject the message and continue processing other clients without crashing
**Validates: Requirements 1.4, 2.5**

**Property 3: Session isolation under concurrent access**
*For any* set of concurrent client sessions, operations on one session should not affect the data or state of other sessions
**Validates: Requirements 1.5, 3.4, 3.5, 8.1**

**Property 4: EEG data parsing and validation**
*For any* valid EEG data packet, the system should correctly parse timestamp and signal data and add them to the appropriate session buffer
**Validates: Requirements 2.1, 2.2, 2.3**

**Property 5: Buffer size limit enforcement with FIFO behavior**
*For any* session buffer that exceeds 2000 samples, the system should remove the oldest samples to maintain the size limit while preserving FIFO ordering
**Validates: Requirements 2.4**

**Property 6: Music generation trigger conditions**
*For any* session with 1280+ samples and 30+ seconds since last generation, the system should trigger music generation using exactly the most recent 1280 samples
**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

**Property 7: Python service request format and timeout**
*For any* music generation request, the system should POST correctly formatted JSON to the Python service with a 30-second timeout
**Validates: Requirements 5.1, 5.2, 5.3**

**Property 8: Response parsing and error handling**
*For any* Python service response (success or failure), the system should parse it correctly or handle errors gracefully without crashing
**Validates: Requirements 5.4, 5.5**

**Property 9: Music response format and delivery**
*For any* music generation result, the system should create a properly formatted Music_Response with all required fields and deliver it immediately via WebSocket
**Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**

**Property 10: Complete session cleanup on connection close**
*For any* WebSocket connection closure, the system should immediately remove all session data, cancel pending requests, and free all associated resources
**Validates: Requirements 7.1, 7.2, 7.3, 7.5**

**Property 11: Idle session cleanup**
*For any* session idle for more than 1 minute, the system should automatically close the connection and clean up all associated resources
**Validates: Requirements 7.4**

**Property 12: System stability under resource constraints**
*For any* resource constraint scenario, the system should enforce limits, prioritize active sessions, and maintain stability without crashing
**Validates: Requirements 8.2, 8.3, 8.4, 8.5**

## Error Handling

The system implements comprehensive error handling at multiple levels:

**WebSocket Level**: Connection errors, malformed messages, and client disconnections are handled gracefully without affecting other sessions. Each session is isolated, and errors in one session do not propagate to others.

**Service Level**: The BrainService handles business logic errors such as insufficient buffer data, cooldown violations, and external service failures. All errors are logged and converted to appropriate error responses.

**Integration Level**: Python ML service timeouts, connection failures, and invalid responses are caught and handled. The system never crashes due to external service issues and always provides meaningful error messages to clients.

**Resource Management**: Memory pressure and resource constraints trigger automatic cleanup of stale sessions and enforcement of buffer limits. The system prioritizes active sessions and maintains stability under load.

**Concurrency**: Thread safety is ensured through concurrent data structures and atomic operations. Race conditions are prevented, and data integrity is maintained across all concurrent operations.

## Testing Strategy

The testing approach combines unit testing for specific scenarios with property-based testing for universal behaviors:

**Unit Testing Framework**: JUnit 5 with Spring Boot Test for integration testing
**Property-Based Testing Framework**: jqwik for Java property-based testing with minimum 100 iterations per property
**WebSocket Testing**: Spring WebSocket Test support for connection and message testing
**HTTP Client Testing**: WebTestClient for testing Python service integration

**Unit Testing Coverage**:
- Specific examples of EEG data processing
- Edge cases like empty buffers, maximum buffer sizes, and boundary conditions
- Integration points between WebSocket handlers and services
- Error scenarios with specific malformed inputs

**Property-Based Testing Coverage**:
- Universal properties that must hold across all valid inputs
- Concurrent access scenarios with multiple sessions
- Buffer management across various data sizes and patterns
- Error handling across different failure modes
- Resource cleanup across different connection patterns

**Test Configuration**:
- Each property-based test runs minimum 100 iterations
- Tests are tagged with comments referencing design document properties
- Format: `**Feature: brain-music-streaming, Property {number}: {property_text}**`
- Each correctness property is implemented by exactly one property-based test

**Integration Testing**:
- End-to-end WebSocket communication flows
- Python service integration with mock responses
- Session lifecycle management from connection to cleanup
- Concurrent client scenarios with multiple simultaneous connections