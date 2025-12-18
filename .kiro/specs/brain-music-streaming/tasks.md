# Implementation Plan

- [x] 1. Set up core data models and validation





  - Create EEGDataPacket DTO with timestamp and signalData fields
  - Create MusicResponse DTO with audioBase64, status, and message fields  
  - Create SessionData class with thread-safe ConcurrentLinkedDeque buffer
  - Add validation annotations and error handling for data models
  - _Requirements: 2.1, 2.2, 6.2_

- [x] 1.1 Write property test for EEG data parsing and validation



  - **Property 4: EEG data parsing and validation**
  - **Validates: Requirements 2.1, 2.2, 2.3**

- [x] 1.2 Write property test for music response format



  - **Property 9: Music response format and delivery**  
  - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**

- [x] 2. Implement session management with thread safety





  - Create SessionManager component with ConcurrentHashMap for session storage
  - Implement createSession, getSession, removeSession, and getActiveSessions methods
  - Add thread-safe session data access with proper synchronization
  - Implement session timeout tracking with lastActivityTime
  - _Requirements: 1.1, 1.3, 3.1, 3.4, 3.5_

- [x] 2.1 Write property test for session initialization



  - **Property 1: Session initialization creates correct state**
  - **Validates: Requirements 1.1, 1.3**

- [x] 2.2 Write property test for session isolation


  - **Property 3: Session isolation under concurrent access**
  - **Validates: Requirements 1.5, 3.4, 3.5, 8.1**

- [x] 3. Create WebSocket configuration and handler





  - Implement WebSocketConfig class extending WebSocketConfigurer
  - Configure CORS to allow all origins for frontend connections
  - Register BrainWaveHandler at /ws/brain-waves endpoint
  - Create BrainWaveHandler extending TextWebSocketHandler
  - Implement handleTextMessage for EEG data processing
  - _Requirements: 1.1, 1.2, 2.1_

- [x] 3.1 Write property test for malformed input handling



  - **Property 2: Malformed input handling preserves system stability**
  - **Validates: Requirements 1.4, 2.5**

- [x] 4. Implement core BrainService business logic





  - Create BrainService with processEEGData method for buffer management
  - Implement buffer size limit enforcement (2000 samples max) with FIFO removal
  - Add music generation trigger logic (1280 samples + 30 second cooldown)
  - Implement sample extraction (most recent 1280 samples)
  - Add timestamp tracking for generation cooldown enforcement
  - _Requirements: 2.3, 2.4, 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 4.1 Write property test for buffer size limit enforcement



  - **Property 5: Buffer size limit enforcement with FIFO behavior**
  - **Validates: Requirements 2.4**

- [x] 4.2 Write property test for music generation trigger conditions


  - **Property 6: Music generation trigger conditions**
  - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

- [x] 5. Implement Python ML service integration





  - Create PythonMLClient using WebClient for HTTP communication
  - Implement predict method with POST to http://localhost:8000/predict
  - Add JSON payload formatting with "signal" field containing float array
  - Configure 30-second timeout for HTTP requests
  - Implement response parsing for "audio_base64" field extraction
  - Add comprehensive error handling for service failures and timeouts
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 5.1 Write property test for Python service request format



  - **Property 7: Python service request format and timeout**
  - **Validates: Requirements 5.1, 5.2, 5.3**

- [x] 5.2 Write property test for response parsing and error handling



  - **Property 8: Response parsing and error handling**
  - **Validates: Requirements 5.4, 5.5**

- [x] 6. Integrate components and implement WebSocket message flow





  - Wire BrainService and PythonMLClient together in BrainWaveHandler
  - Implement complete message processing flow from EEG data to music response
  - Add WebSocket response sending with JSON serialization
  - Implement connection establishment with session initialization
  - Add proper error response generation and delivery
  - _Requirements: 2.1, 2.3, 6.1, 6.3, 6.5_

- [x] 7. Implement resource cleanup and session management





  - Add afterConnectionClosed handler for immediate session cleanup
  - Implement session data removal and buffer clearing
  - Add pending request cancellation for closed connections
  - Create scheduled task for idle session cleanup (1+ minute timeout)
  - Implement comprehensive resource cleanup to prevent memory leaks
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 7.1 Write property test for session cleanup on connection close



  - **Property 10: Complete session cleanup on connection close**
  - **Validates: Requirements 7.1, 7.2, 7.3, 7.5**

- [x] 7.2 Write property test for idle session cleanup



  - **Property 11: Idle session cleanup**
  - **Validates: Requirements 7.4**

- [x] 8. Add system stability and error handling





  - Implement global exception handling for WebSocket operations
  - Add error isolation to prevent one client affecting others
  - Implement resource constraint handling with buffer limits
  - Add system stability measures for external service failures
  - Create monitoring and logging for system health
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 8.1 Write property test for system stability under resource constraints



  - **Property 12: System stability under resource constraints**
  - **Validates: Requirements 8.2, 8.3, 8.4, 8.5**

- [x] 9. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Create integration tests for end-to-end flows






  - Write integration tests for complete WebSocket communication flows
  - Test Python service integration with mock responses
  - Verify session lifecycle from connection to cleanup
  - Test concurrent client scenarios with multiple connections
  - _Requirements: All requirements integration testing_

- [x] 11. Final checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.