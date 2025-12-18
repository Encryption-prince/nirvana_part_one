# Requirements Document

## Introduction

The Brain-to-Music biofeedback application is a real-time system that processes EEG (electroencephalogram) data streams from frontend clients via WebSocket connections, buffers the neural signal data using a sliding window approach, and generates music by sending processed data to an external Python ML service. The system streams the generated music back to clients as Base64-encoded audio data, creating an interactive biofeedback experience where brain activity directly influences musical output.

## Glossary

- **EEG_System**: The Spring Boot backend application that processes brain wave data and generates music
- **EEG_Data_Packet**: A data structure containing timestamp and signal data from brain wave sensors
- **Music_Response**: A data structure containing Base64-encoded audio data and status information
- **Session_Buffer**: A thread-safe queue that stores EEG signal data for each connected client
- **Python_ML_Service**: External machine learning service that converts EEG signals into music
- **WebSocket_Handler**: Component that manages real-time bidirectional communication with clients
- **Brain_Service**: Core business logic component that manages EEG data processing and music generation
- **Session_Manager**: Component that manages client sessions and their associated data buffers

## Requirements

### Requirement 1

**User Story:** As a frontend client, I want to establish a WebSocket connection to stream EEG data, so that I can send real-time brain wave measurements to the system.

#### Acceptance Criteria

1. WHEN a client connects to the /ws/brain-waves endpoint THEN the EEG_System SHALL establish a WebSocket connection and create a new session
2. WHEN establishing WebSocket connections THEN the EEG_System SHALL allow all origins to prevent CORS issues
3. WHEN a WebSocket connection is established THEN the EEG_System SHALL initialize an empty Session_Buffer for that client
4. WHEN a client sends malformed JSON data THEN the EEG_System SHALL reject the message and maintain connection stability
5. WHEN multiple clients connect simultaneously THEN the EEG_System SHALL handle each session independently without interference

### Requirement 2

**User Story:** As a frontend client, I want to send EEG data packets through the WebSocket connection, so that the system can process my brain wave data for music generation.

#### Acceptance Criteria

1. WHEN a client sends an EEG_Data_Packet THEN the EEG_System SHALL parse the timestamp and signal data fields
2. WHEN parsing EEG data THEN the EEG_System SHALL validate that timestamp is a valid long value and signalData is a list of floats
3. WHEN valid EEG data is received THEN the EEG_System SHALL add the signal data to the client's Session_Buffer
4. WHEN the Session_Buffer exceeds 2000 samples THEN the EEG_System SHALL remove the oldest samples to maintain the buffer limit
5. WHEN invalid EEG data is received THEN the EEG_System SHALL log the error and continue processing without crashing

### Requirement 3

**User Story:** As the system, I want to maintain thread-safe data buffers for each client session, so that concurrent access to EEG data does not cause data corruption or race conditions.

#### Acceptance Criteria

1. WHEN multiple threads access a Session_Buffer THEN the EEG_System SHALL ensure thread-safe operations using concurrent data structures
2. WHEN storing session data THEN the EEG_System SHALL use a ConcurrentHashMap to map session IDs to SessionData objects
3. WHEN managing EEG signal buffers THEN the EEG_System SHALL use ConcurrentLinkedDeque for thread-safe queue operations
4. WHEN concurrent buffer modifications occur THEN the EEG_System SHALL maintain data integrity without blocking operations
5. WHEN accessing session data across threads THEN the EEG_System SHALL prevent race conditions and ensure consistent state

### Requirement 4

**User Story:** As the system, I want to trigger music generation when sufficient EEG data is collected, so that I can provide timely audio feedback based on brain activity patterns.

#### Acceptance Criteria

1. WHEN the Session_Buffer contains 1280 or more samples THEN the EEG_System SHALL check if music generation can be triggered
2. WHEN checking generation eligibility THEN the EEG_System SHALL verify that at least 30 seconds have passed since the last generation
3. WHEN generation conditions are met THEN the EEG_System SHALL extract exactly 1280 samples from the buffer for processing
4. WHEN extracting samples THEN the EEG_System SHALL take the most recent 1280 samples from the Session_Buffer
5. WHEN generation is triggered THEN the EEG_System SHALL update the last generation timestamp to enforce cooldown period

### Requirement 5

**User Story:** As the system, I want to communicate with the external Python ML service, so that I can convert processed EEG signals into musical audio data.

#### Acceptance Criteria

1. WHEN sending EEG data to the Python_ML_Service THEN the EEG_System SHALL POST JSON payload to http://localhost:8000/predict
2. WHEN creating the request payload THEN the EEG_System SHALL format the signal data as a JSON object with "signal" field containing float array
3. WHEN making HTTP requests THEN the EEG_System SHALL set a timeout of 30 seconds to prevent hanging connections
4. WHEN the Python_ML_Service responds THEN the EEG_System SHALL parse the JSON response containing "audio_base64" field
5. WHEN the Python_ML_Service fails or times out THEN the EEG_System SHALL handle the error gracefully and return an error response to the client

### Requirement 6

**User Story:** As a frontend client, I want to receive generated music as Base64-encoded audio data, so that I can play the music that corresponds to my brain wave patterns.

#### Acceptance Criteria

1. WHEN music generation completes successfully THEN the EEG_System SHALL send a Music_Response containing the Base64 audio data
2. WHEN creating Music_Response THEN the EEG_System SHALL include audioBase64, status, and message fields
3. WHEN sending responses via WebSocket THEN the EEG_System SHALL serialize the Music_Response as JSON
4. WHEN music generation fails THEN the EEG_System SHALL send an error Music_Response with appropriate status and message
5. WHEN responses are sent THEN the EEG_System SHALL ensure immediate delivery to the requesting client

### Requirement 7

**User Story:** As the system, I want to properly clean up resources when clients disconnect, so that I can prevent memory leaks and maintain system performance.

#### Acceptance Criteria

1. WHEN a WebSocket connection closes THEN the EEG_System SHALL immediately remove the client's session data from memory
2. WHEN cleaning up sessions THEN the EEG_System SHALL clear the Session_Buffer and remove the session from the ConcurrentHashMap
3. WHEN a connection closes during pending Python requests THEN the EEG_System SHALL cancel or ignore the response to prevent errors
4. WHEN sessions become idle for more than 1 minute THEN the EEG_System SHALL automatically close stale connections and clean up resources
5. WHEN performing cleanup operations THEN the EEG_System SHALL ensure no memory leaks or resource retention occurs

### Requirement 8

**User Story:** As a system administrator, I want the system to handle errors gracefully and maintain stability, so that individual client issues do not affect the overall system performance.

#### Acceptance Criteria

1. WHEN exceptions occur during WebSocket message processing THEN the EEG_System SHALL log the error and continue serving other clients
2. WHEN the Python_ML_Service is unavailable THEN the EEG_System SHALL return appropriate error messages without crashing
3. WHEN memory pressure occurs THEN the EEG_System SHALL enforce buffer limits and cleanup stale sessions
4. WHEN concurrent access conflicts arise THEN the EEG_System SHALL resolve them using thread-safe mechanisms
5. WHEN system resources are constrained THEN the EEG_System SHALL prioritize active sessions and clean up inactive ones