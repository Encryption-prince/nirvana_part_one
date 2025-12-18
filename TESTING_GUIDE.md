# Brain-to-Music Streaming Application - Testing Guide

## Overview

This guide provides comprehensive instructions for testing the Brain-to-Music biofeedback application. The system processes EEG (electroencephalogram) data streams via WebSocket connections and generates music using an external Python ML service.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Running the Application](#running-the-application)
3. [Testing with WebSocket Clients](#testing-with-websocket-clients)
4. [Testing with Postman](#testing-with-postman)
5. [API Reference](#api-reference)
6. [Test Scenarios](#test-scenarios)
7. [Automated Testing](#automated-testing)
8. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software
- Java 21 or higher
- Maven 3.6+
- Python 3.8+ (for ML service)
- WebSocket client (Postman, browser, or custom client)

### Optional Python ML Service
The application can run without the Python ML service, but music generation will return error responses. To set up a mock Python service:

```python
# mock_ml_service.py
from flask import Flask, request, jsonify
import base64
import json

app = Flask(__name__)

@app.route('/predict', methods=['POST'])
def predict():
    data = request.get_json()
    signal = data.get('signal', [])
    
    # Mock audio data (Base64 encoded "test audio data")
    mock_audio = base64.b64encode(b"test audio data").decode('utf-8')
    
    return jsonify({
        "audio_base64": mock_audio
    })

if __name__ == '__main__':
    app.run(host='localhost', port=8000, debug=True)
```

Run with: `python mock_ml_service.py`

## Running the Application

### 1. Start the Spring Boot Application

```bash
# Clone and navigate to project directory
cd nirvana

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080` by default.

### 2. Verify Application Startup

Check the logs for successful startup:
```
INFO  --- [main] c.b.nirvana.NirvanaApplication : Started NirvanaApplication in X.XXX seconds
INFO  --- [main] o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8080 (http)
```

## Testing with WebSocket Clients

### WebSocket Endpoint
- **URL**: `ws://localhost:8080/ws/brain-waves`
- **Protocol**: WebSocket
- **Message Format**: JSON

### Message Structure

#### Input Message (EEG Data Packet)
```json
{
  "timestamp": 1703123456789,
  "signalData": [1.5, 2.3, -0.8, 4.2, 1.1, ...]
}
```

#### Output Message (Music Response)
```json
{
  "audioBase64": "dGVzdCBhdWRpbyBkYXRh",
  "status": "success",
  "message": "Music generated successfully"
}
```

#### Error Response
```json
{
  "audioBase64": null,
  "status": "error",
  "message": "Error description"
}
```

## Testing with Postman

### Setting Up WebSocket Connection in Postman

1. **Create New WebSocket Request**
   - Click "New" → "WebSocket Request"
   - Enter URL: `ws://localhost:8080/ws/brain-waves`
   - Click "Connect"

2. **Verify Connection**
   - Status should show "Connected"
   - Check application logs for connection establishment

### Test Scenarios in Postman

#### Scenario 1: Basic EEG Data Processing

**Send Message:**
```json
{
  "timestamp": 1703123456789,
  "signalData": [1.5, 2.3, -0.8, 4.2, 1.1, 0.9, 2.1, -1.3, 3.4, 0.7]
}
```

**Expected Response:**
- No immediate response (insufficient data for music generation)
- Check logs for successful data processing

#### Scenario 2: Trigger Music Generation

**Send Message with 1280+ samples:**
```json
{
  "timestamp": 1703123456789,
  "signalData": [/* Array of 1280 float values */]
}
```

**Generate sample data using this JavaScript snippet in Postman:**
```javascript
// Pre-request Script in Postman
const signalData = [];
for (let i = 0; i < 1280; i++) {
    signalData.push(Math.sin(i * 0.1) * 100);
}

pm.globals.set("signalData", JSON.stringify(signalData));
```

**Message Body:**
```json
{
  "timestamp": {{$timestamp}},
  "signalData": {{signalData}}
}
```

**Expected Response:**
- Success response with Base64 audio data (if Python service is running)
- Error response if Python service is unavailable

#### Scenario 3: Error Handling Tests

**Test Invalid JSON:**
```
invalid json message
```

**Expected Response:**
```json
{
  "audioBase64": null,
  "status": "error",
  "message": "Invalid message format"
}
```

**Test Null Signal Data:**
```json
{
  "timestamp": 1703123456789,
  "signalData": null
}
```

**Expected Response:**
```json
{
  "audioBase64": null,
  "status": "error",
  "message": "Invalid EEG data: signal data is null or empty"
}
```

**Test Empty Signal Data:**
```json
{
  "timestamp": 1703123456789,
  "signalData": []
}
```

**Expected Response:**
```json
{
  "audioBase64": null,
  "status": "error",
  "message": "Invalid EEG data: signal data is null or empty"
}
```

#### Scenario 4: Cooldown Testing

1. **Send first message with 1280+ samples** (triggers generation)
2. **Immediately send second message with 1280+ samples**
3. **Expected**: No response to second message due to 30-second cooldown

#### Scenario 5: Buffer Limit Testing

**Send multiple messages totaling more than 2000 samples:**
```json
// Send this message 25 times (25 * 100 = 2500 samples)
{
  "timestamp": 1703123456789,
  "signalData": [/* Array of 100 float values */]
}
```

**Expected**: Buffer should be limited to 2000 samples (FIFO behavior)

## API Reference

### WebSocket Events

| Event | Description |
|-------|-------------|
| `connection` | Client connects to WebSocket endpoint |
| `message` | Client sends EEG data packet |
| `close` | Client disconnects (triggers cleanup) |
| `error` | Connection error occurs |

### Response Status Codes

| Status | Description |
|--------|-------------|
| `success` | Music generated successfully |
| `error` | Error occurred during processing |
| `cooldown` | Generation blocked by cooldown period |
| `buffering` | Data received but insufficient for generation |

### System Behavior

| Condition | Behavior |
|-----------|----------|
| < 1280 samples | No music generation, data buffered |
| ≥ 1280 samples + no cooldown | Music generation triggered |
| ≥ 1280 samples + cooldown active | No generation, data buffered |
| > 2000 samples in buffer | Oldest samples removed (FIFO) |
| Invalid data | Error response sent |
| Connection closed | Session cleanup performed |

## Test Scenarios

### Functional Testing

1. **Connection Management**
   - ✅ Establish WebSocket connection
   - ✅ Handle multiple concurrent connections
   - ✅ Clean up on connection close

2. **Data Processing**
   - ✅ Process valid EEG data packets
   - ✅ Validate input data format
   - ✅ Handle malformed JSON
   - ✅ Handle null/empty signal data

3. **Buffer Management**
   - ✅ Accumulate signal data in session buffer
   - ✅ Enforce 2000 sample buffer limit
   - ✅ Implement FIFO removal of old samples

4. **Music Generation**
   - ✅ Trigger generation with 1280+ samples
   - ✅ Enforce 30-second cooldown period
   - ✅ Handle Python service failures gracefully

5. **Error Handling**
   - ✅ Isolate client errors
   - ✅ Maintain system stability
   - ✅ Provide meaningful error messages

### Performance Testing

1. **Concurrent Connections**
   - Test with multiple simultaneous WebSocket connections
   - Verify session isolation
   - Monitor memory usage

2. **High-Frequency Data**
   - Send rapid EEG data packets
   - Verify buffer management
   - Check for memory leaks

3. **Long-Running Sessions**
   - Maintain connections for extended periods
   - Verify automatic cleanup of idle sessions
   - Monitor resource utilization

## Automated Testing

### Running Unit Tests
```bash
mvn test -Dtest="!*PropertyTest"
```

### Running Property-Based Tests
```bash
mvn test -Dtest="*PropertyTest"
```

### Running Integration Tests
```bash
mvn test -Dtest="*IntegrationTest"
```

### Running All Tests
```bash
mvn test
```

### Test Coverage
The application includes comprehensive test coverage:
- **Unit Tests**: Individual component testing
- **Property-Based Tests**: Universal behavior validation
- **Integration Tests**: End-to-end flow testing

## Troubleshooting

### Common Issues

#### WebSocket Connection Fails
- **Check**: Application is running on correct port
- **Check**: No firewall blocking WebSocket connections
- **Solution**: Verify URL format: `ws://localhost:8080/ws/brain-waves`

#### No Music Generation Response
- **Check**: Sent at least 1280 samples
- **Check**: 30-second cooldown period has elapsed
- **Check**: Python ML service is running (if expected)
- **Solution**: Review application logs for detailed error messages

#### Memory Issues
- **Check**: Multiple long-running sessions
- **Check**: Buffer limits being enforced
- **Solution**: Monitor session cleanup and buffer management

#### JSON Parsing Errors
- **Check**: Message format matches expected structure
- **Check**: All required fields are present
- **Solution**: Validate JSON structure before sending

### Logging

Enable debug logging for detailed troubleshooting:
```yaml
# application.yaml
logging:
  level:
    com.backend.nirvana: DEBUG
```

### Health Monitoring

The application includes built-in health monitoring:
- Memory usage tracking
- Active session counting
- System stability monitoring

Check logs for health status updates every 30 seconds.

## Example Test Scripts

### JavaScript WebSocket Client
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/brain-waves');

ws.onopen = function() {
    console.log('Connected to Brain-Music WebSocket');
    
    // Generate test EEG data
    const signalData = [];
    for (let i = 0; i < 1280; i++) {
        signalData.push(Math.sin(i * 0.1) * 100);
    }
    
    // Send EEG data packet
    const eegPacket = {
        timestamp: Date.now(),
        signalData: signalData
    };
    
    ws.send(JSON.stringify(eegPacket));
};

ws.onmessage = function(event) {
    const response = JSON.parse(event.data);
    console.log('Received response:', response);
    
    if (response.status === 'success') {
        console.log('Music generated! Audio data length:', response.audioBase64.length);
    } else {
        console.log('Error:', response.message);
    }
};

ws.onclose = function() {
    console.log('WebSocket connection closed');
};

ws.onerror = function(error) {
    console.error('WebSocket error:', error);
};
```

### Python Test Client
```python
import websocket
import json
import math
import time

def on_message(ws, message):
    response = json.loads(message)
    print(f"Received: {response}")

def on_error(ws, error):
    print(f"Error: {error}")

def on_close(ws, close_status_code, close_msg):
    print("Connection closed")

def on_open(ws):
    print("Connected to Brain-Music WebSocket")
    
    # Generate test EEG data
    signal_data = [math.sin(i * 0.1) * 100 for i in range(1280)]
    
    # Send EEG data packet
    eeg_packet = {
        "timestamp": int(time.time() * 1000),
        "signalData": signal_data
    }
    
    ws.send(json.dumps(eeg_packet))

# Create WebSocket connection
ws = websocket.WebSocketApp("ws://localhost:8080/ws/brain-waves",
                          on_open=on_open,
                          on_message=on_message,
                          on_error=on_error,
                          on_close=on_close)

ws.run_forever()
```

## Conclusion

This testing guide provides comprehensive coverage for validating the Brain-to-Music streaming application. The system is designed to handle real-time EEG data processing with robust error handling, session management, and resource cleanup.

For additional support or questions, refer to the application logs and the comprehensive test suite included in the project.