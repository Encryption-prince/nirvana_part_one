# Frontend Integration Guide - Brain-to-Music Streaming

## Overview

This guide shows how frontend applications can connect to the Brain-to-Music streaming backend using WebSocket connections. The backend exposes a WebSocket endpoint at `/ws/brain-waves` that accepts EEG data and returns generated music.

## Connection Architecture

```
Frontend Application
        ↓ WebSocket Connection
        ↓ ws://localhost:8080/ws/brain-waves
Backend Spring Boot App
        ↓ HTTP Request
        ↓ http://localhost:8000/predict
Python ML Service
```

## WebSocket Connection Flow

### 1. Connection Establishment
```javascript
// Frontend connects to WebSocket
const ws = new WebSocket('ws://localhost:8080/ws/brain-waves');
```

### 2. Data Flow
```
Frontend → Backend: EEG Data Packet (JSON)
Backend → Frontend: Music Response (JSON) or No Response
```

### 3. Session Management
- Each WebSocket connection creates a unique session
- Session automatically cleaned up on disconnect
- Buffer maintained per session (max 2000 samples)

## Frontend Implementation Examples

### 1. Vanilla JavaScript (Web Browser)

```html
<!DOCTYPE html>
<html>
<head>
    <title>Brain-Music Streaming Client</title>
</head>
<body>
    <div id="status">Disconnected</div>
    <button id="connect">Connect</button>
    <button id="disconnect">Disconnect</button>
    <button id="sendData">Send EEG Data</button>
    <button id="sendLargeData">Trigger Music Generation</button>
    <div id="messages"></div>
    <audio id="audioPlayer" controls style="display:none;"></audio>

    <script>
        class BrainMusicClient {
            constructor() {
                this.ws = null;
                this.isConnected = false;
                this.setupUI();
            }

            setupUI() {
                document.getElementById('connect').onclick = () => this.connect();
                document.getElementById('disconnect').onclick = () => this.disconnect();
                document.getElementById('sendData').onclick = () => this.sendSampleData();
                document.getElementById('sendLargeData').onclick = () => this.sendLargeData();
            }

            connect() {
                if (this.isConnected) return;

                this.ws = new WebSocket('ws://localhost:8080/ws/brain-waves');
                
                this.ws.onopen = () => {
                    this.isConnected = true;
                    this.updateStatus('Connected');
                    this.log('✅ Connected to Brain-Music WebSocket');
                };

                this.ws.onmessage = (event) => {
                    const response = JSON.parse(event.data);
                    this.handleMusicResponse(response);
                };

                this.ws.onclose = () => {
                    this.isConnected = false;
                    this.updateStatus('Disconnected');
                    this.log('❌ WebSocket connection closed');
                };

                this.ws.onerror = (error) => {
                    this.log('🚨 WebSocket error: ' + error);
                };
            }

            disconnect() {
                if (this.ws) {
                    this.ws.close();
                }
            }

            sendEEGData(signalData) {
                if (!this.isConnected) {
                    this.log('❌ Not connected');
                    return;
                }

                const eegPacket = {
                    timestamp: Date.now(),
                    signalData: signalData
                };

                this.ws.send(JSON.stringify(eegPacket));
                this.log(`📡 Sent EEG data: ${signalData.length} samples`);
            }

            sendSampleData() {
                // Send small sample (won't trigger music generation)
                const sampleData = [];
                for (let i = 0; i < 100; i++) {
                    sampleData.push(Math.sin(i * 0.1) * 50 + Math.random() * 10);
                }
                this.sendEEGData(sampleData);
            }

            sendLargeData() {
                // Send 1280+ samples to trigger music generation
                const largeData = [];
                for (let i = 0; i < 1280; i++) {
                    largeData.push(Math.sin(i * 0.05) * 100 + Math.random() * 20);
                }
                this.sendEEGData(largeData);
            }

            handleMusicResponse(response) {
                this.log(`🎵 Received response: ${response.status}`);
                
                if (response.status === 'success' && response.audioBase64) {
                    this.playAudio(response.audioBase64);
                    this.log('🎶 Playing generated music!');
                } else if (response.status === 'error') {
                    this.log(`❌ Error: ${response.message}`);
                }
            }

            playAudio(audioBase64) {
                // Convert Base64 to audio blob and play
                const audioData = atob(audioBase64);
                const audioArray = new Uint8Array(audioData.length);
                for (let i = 0; i < audioData.length; i++) {
                    audioArray[i] = audioData.charCodeAt(i);
                }
                
                const audioBlob = new Blob([audioArray], { type: 'audio/wav' });
                const audioUrl = URL.createObjectURL(audioBlob);
                
                const audioPlayer = document.getElementById('audioPlayer');
                audioPlayer.src = audioUrl;
                audioPlayer.style.display = 'block';
                audioPlayer.play();
            }

            updateStatus(status) {
                document.getElementById('status').textContent = status;
            }

            log(message) {
                const messagesDiv = document.getElementById('messages');
                messagesDiv.innerHTML += `<div>${new Date().toLocaleTimeString()}: ${message}</div>`;
                messagesDiv.scrollTop = messagesDiv.scrollHeight;
            }
        }

        // Initialize client
        const client = new BrainMusicClient();
    </script>
</body>
</html>
```

### 2. React.js Implementation

```jsx
import React, { useState, useEffect, useRef } from 'react';

const BrainMusicClient = () => {
    const [isConnected, setIsConnected] = useState(false);
    const [messages, setMessages] = useState([]);
    const [audioUrl, setAudioUrl] = useState(null);
    const wsRef = useRef(null);

    const addMessage = (message) => {
        setMessages(prev => [...prev, `${new Date().toLocaleTimeString()}: ${message}`]);
    };

    const connect = () => {
        if (wsRef.current?.readyState === WebSocket.OPEN) return;

        wsRef.current = new WebSocket('ws://localhost:8080/ws/brain-waves');
        
        wsRef.current.onopen = () => {
            setIsConnected(true);
            addMessage('✅ Connected to Brain-Music WebSocket');
        };

        wsRef.current.onmessage = (event) => {
            const response = JSON.parse(event.data);
            handleMusicResponse(response);
        };

        wsRef.current.onclose = () => {
            setIsConnected(false);
            addMessage('❌ WebSocket connection closed');
        };

        wsRef.current.onerror = (error) => {
            addMessage('🚨 WebSocket error: ' + error);
        };
    };

    const disconnect = () => {
        if (wsRef.current) {
            wsRef.current.close();
        }
    };

    const sendEEGData = (signalData) => {
        if (!isConnected) {
            addMessage('❌ Not connected');
            return;
        }

        const eegPacket = {
            timestamp: Date.now(),
            signalData: signalData
        };

        wsRef.current.send(JSON.stringify(eegPacket));
        addMessage(`📡 Sent EEG data: ${signalData.length} samples`);
    };

    const sendSampleData = () => {
        const sampleData = Array.from({ length: 100 }, (_, i) => 
            Math.sin(i * 0.1) * 50 + Math.random() * 10
        );
        sendEEGData(sampleData);
    };

    const sendLargeData = () => {
        const largeData = Array.from({ length: 1280 }, (_, i) => 
            Math.sin(i * 0.05) * 100 + Math.random() * 20
        );
        sendEEGData(largeData);
    };

    const handleMusicResponse = (response) => {
        addMessage(`🎵 Received response: ${response.status}`);
        
        if (response.status === 'success' && response.audioBase64) {
            playAudio(response.audioBase64);
            addMessage('🎶 Playing generated music!');
        } else if (response.status === 'error') {
            addMessage(`❌ Error: ${response.message}`);
        }
    };

    const playAudio = (audioBase64) => {
        try {
            const audioData = atob(audioBase64);
            const audioArray = new Uint8Array(audioData.length);
            for (let i = 0; i < audioData.length; i++) {
                audioArray[i] = audioData.charCodeAt(i);
            }
            
            const audioBlob = new Blob([audioArray], { type: 'audio/wav' });
            const url = URL.createObjectURL(audioBlob);
            setAudioUrl(url);
        } catch (error) {
            addMessage(`❌ Error playing audio: ${error.message}`);
        }
    };

    useEffect(() => {
        return () => {
            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, []);

    return (
        <div style={{ padding: '20px', fontFamily: 'Arial, sans-serif' }}>
            <h1>Brain-Music Streaming Client</h1>
            
            <div style={{ marginBottom: '20px' }}>
                <strong>Status: </strong>
                <span style={{ color: isConnected ? 'green' : 'red' }}>
                    {isConnected ? 'Connected' : 'Disconnected'}
                </span>
            </div>

            <div style={{ marginBottom: '20px' }}>
                <button onClick={connect} disabled={isConnected}>
                    Connect
                </button>
                <button onClick={disconnect} disabled={!isConnected} style={{ marginLeft: '10px' }}>
                    Disconnect
                </button>
                <button onClick={sendSampleData} disabled={!isConnected} style={{ marginLeft: '10px' }}>
                    Send Sample Data
                </button>
                <button onClick={sendLargeData} disabled={!isConnected} style={{ marginLeft: '10px' }}>
                    Trigger Music Generation
                </button>
            </div>

            {audioUrl && (
                <div style={{ marginBottom: '20px' }}>
                    <audio controls src={audioUrl} />
                </div>
            )}

            <div style={{ 
                height: '300px', 
                overflow: 'auto', 
                border: '1px solid #ccc', 
                padding: '10px',
                backgroundColor: '#f9f9f9'
            }}>
                {messages.map((message, index) => (
                    <div key={index}>{message}</div>
                ))}
            </div>
        </div>
    );
};

export default BrainMusicClient;
```

### 3. Vue.js Implementation

```vue
<template>
  <div class="brain-music-client">
    <h1>Brain-Music Streaming Client</h1>
    
    <div class="status">
      <strong>Status: </strong>
      <span :class="{ connected: isConnected, disconnected: !isConnected }">
        {{ isConnected ? 'Connected' : 'Disconnected' }}
      </span>
    </div>

    <div class="controls">
      <button @click="connect" :disabled="isConnected">Connect</button>
      <button @click="disconnect" :disabled="!isConnected">Disconnect</button>
      <button @click="sendSampleData" :disabled="!isConnected">Send Sample Data</button>
      <button @click="sendLargeData" :disabled="!isConnected">Trigger Music Generation</button>
    </div>

    <audio v-if="audioUrl" :src="audioUrl" controls class="audio-player"></audio>

    <div class="messages" ref="messagesContainer">
      <div v-for="(message, index) in messages" :key="index">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BrainMusicClient',
  data() {
    return {
      ws: null,
      isConnected: false,
      messages: [],
      audioUrl: null
    };
  },
  methods: {
    connect() {
      if (this.isConnected) return;

      this.ws = new WebSocket('ws://localhost:8080/ws/brain-waves');
      
      this.ws.onopen = () => {
        this.isConnected = true;
        this.addMessage('✅ Connected to Brain-Music WebSocket');
      };

      this.ws.onmessage = (event) => {
        const response = JSON.parse(event.data);
        this.handleMusicResponse(response);
      };

      this.ws.onclose = () => {
        this.isConnected = false;
        this.addMessage('❌ WebSocket connection closed');
      };

      this.ws.onerror = (error) => {
        this.addMessage('🚨 WebSocket error: ' + error);
      };
    },

    disconnect() {
      if (this.ws) {
        this.ws.close();
      }
    },

    sendEEGData(signalData) {
      if (!this.isConnected) {
        this.addMessage('❌ Not connected');
        return;
      }

      const eegPacket = {
        timestamp: Date.now(),
        signalData: signalData
      };

      this.ws.send(JSON.stringify(eegPacket));
      this.addMessage(`📡 Sent EEG data: ${signalData.length} samples`);
    },

    sendSampleData() {
      const sampleData = Array.from({ length: 100 }, (_, i) => 
        Math.sin(i * 0.1) * 50 + Math.random() * 10
      );
      this.sendEEGData(sampleData);
    },

    sendLargeData() {
      const largeData = Array.from({ length: 1280 }, (_, i) => 
        Math.sin(i * 0.05) * 100 + Math.random() * 20
      );
      this.sendEEGData(largeData);
    },

    handleMusicResponse(response) {
      this.addMessage(`🎵 Received response: ${response.status}`);
      
      if (response.status === 'success' && response.audioBase64) {
        this.playAudio(response.audioBase64);
        this.addMessage('🎶 Playing generated music!');
      } else if (response.status === 'error') {
        this.addMessage(`❌ Error: ${response.message}`);
      }
    },

    playAudio(audioBase64) {
      try {
        const audioData = atob(audioBase64);
        const audioArray = new Uint8Array(audioData.length);
        for (let i = 0; i < audioData.length; i++) {
          audioArray[i] = audioData.charCodeAt(i);
        }
        
        const audioBlob = new Blob([audioArray], { type: 'audio/wav' });
        this.audioUrl = URL.createObjectURL(audioBlob);
      } catch (error) {
        this.addMessage(`❌ Error playing audio: ${error.message}`);
      }
    },

    addMessage(message) {
      this.messages.push(`${new Date().toLocaleTimeString()}: ${message}`);
      this.$nextTick(() => {
        const container = this.$refs.messagesContainer;
        container.scrollTop = container.scrollHeight;
      });
    }
  },

  beforeUnmount() {
    if (this.ws) {
      this.ws.close();
    }
  }
};
</script>

<style scoped>
.brain-music-client {
  padding: 20px;
  font-family: Arial, sans-serif;
}

.status {
  margin-bottom: 20px;
}

.connected {
  color: green;
}

.disconnected {
  color: red;
}

.controls {
  margin-bottom: 20px;
}

.controls button {
  margin-right: 10px;
  padding: 8px 16px;
}

.audio-player {
  margin-bottom: 20px;
  width: 100%;
}

.messages {
  height: 300px;
  overflow: auto;
  border: 1px solid #ccc;
  padding: 10px;
  background-color: #f9f9f9;
}
</style>
```

### 4. Node.js Backend Integration

```javascript
// server.js - Node.js backend that connects to Java backend
const WebSocket = require('ws');
const express = require('express');
const http = require('http');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// Store client connections
const clients = new Map();

// Connect to Java backend
function connectToJavaBackend(clientId) {
    const javaWs = new WebSocket('ws://localhost:8080/ws/brain-waves');
    
    javaWs.on('open', () => {
        console.log(`Connected to Java backend for client ${clientId}`);
    });
    
    javaWs.on('message', (data) => {
        // Forward response back to client
        const client = clients.get(clientId);
        if (client && client.readyState === WebSocket.OPEN) {
            client.send(data);
        }
    });
    
    javaWs.on('close', () => {
        console.log(`Java backend connection closed for client ${clientId}`);
    });
    
    return javaWs;
}

// Handle client connections
wss.on('connection', (ws) => {
    const clientId = generateClientId();
    clients.set(clientId, ws);
    
    // Connect to Java backend for this client
    const javaWs = connectToJavaBackend(clientId);
    
    ws.on('message', (message) => {
        // Forward EEG data to Java backend
        if (javaWs.readyState === WebSocket.OPEN) {
            javaWs.send(message);
        }
    });
    
    ws.on('close', () => {
        clients.delete(clientId);
        javaWs.close();
    });
});

function generateClientId() {
    return Math.random().toString(36).substring(2, 15);
}

server.listen(3000, () => {
    console.log('Node.js proxy server listening on port 3000');
});
```

### 5. Mobile App Integration (React Native)

```javascript
// BrainMusicClient.js - React Native component
import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Alert } from 'react-native';

const BrainMusicClient = () => {
    const [isConnected, setIsConnected] = useState(false);
    const [ws, setWs] = useState(null);
    const [messages, setMessages] = useState([]);

    const connect = () => {
        if (isConnected) return;

        const websocket = new WebSocket('ws://localhost:8080/ws/brain-waves');
        
        websocket.onopen = () => {
            setIsConnected(true);
            addMessage('✅ Connected to Brain-Music WebSocket');
        };

        websocket.onmessage = (event) => {
            const response = JSON.parse(event.data);
            handleMusicResponse(response);
        };

        websocket.onclose = () => {
            setIsConnected(false);
            addMessage('❌ WebSocket connection closed');
        };

        websocket.onerror = (error) => {
            addMessage('🚨 WebSocket error: ' + error.message);
        };

        setWs(websocket);
    };

    const disconnect = () => {
        if (ws) {
            ws.close();
        }
    };

    const sendEEGData = (signalData) => {
        if (!isConnected) {
            Alert.alert('Error', 'Not connected to server');
            return;
        }

        const eegPacket = {
            timestamp: Date.now(),
            signalData: signalData
        };

        ws.send(JSON.stringify(eegPacket));
        addMessage(`📡 Sent EEG data: ${signalData.length} samples`);
    };

    const sendLargeData = () => {
        const largeData = Array.from({ length: 1280 }, (_, i) => 
            Math.sin(i * 0.05) * 100 + Math.random() * 20
        );
        sendEEGData(largeData);
    };

    const handleMusicResponse = (response) => {
        addMessage(`🎵 Received response: ${response.status}`);
        
        if (response.status === 'success' && response.audioBase64) {
            addMessage('🎶 Music generated successfully!');
            // Handle audio playback in mobile app
            // You would use react-native-sound or similar library
        } else if (response.status === 'error') {
            addMessage(`❌ Error: ${response.message}`);
        }
    };

    const addMessage = (message) => {
        setMessages(prev => [...prev, `${new Date().toLocaleTimeString()}: ${message}`]);
    };

    useEffect(() => {
        return () => {
            if (ws) {
                ws.close();
            }
        };
    }, [ws]);

    return (
        <View style={styles.container}>
            <Text style={styles.title}>Brain-Music Streaming</Text>
            
            <Text style={[styles.status, { color: isConnected ? 'green' : 'red' }]}>
                Status: {isConnected ? 'Connected' : 'Disconnected'}
            </Text>

            <View style={styles.buttonContainer}>
                <TouchableOpacity 
                    style={[styles.button, isConnected && styles.buttonDisabled]} 
                    onPress={connect}
                    disabled={isConnected}
                >
                    <Text style={styles.buttonText}>Connect</Text>
                </TouchableOpacity>

                <TouchableOpacity 
                    style={[styles.button, !isConnected && styles.buttonDisabled]} 
                    onPress={disconnect}
                    disabled={!isConnected}
                >
                    <Text style={styles.buttonText}>Disconnect</Text>
                </TouchableOpacity>

                <TouchableOpacity 
                    style={[styles.button, !isConnected && styles.buttonDisabled]} 
                    onPress={sendLargeData}
                    disabled={!isConnected}
                >
                    <Text style={styles.buttonText}>Generate Music</Text>
                </TouchableOpacity>
            </View>

            <View style={styles.messagesContainer}>
                {messages.slice(-10).map((message, index) => (
                    <Text key={index} style={styles.message}>{message}</Text>
                ))}
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        padding: 20,
        backgroundColor: '#fff',
    },
    title: {
        fontSize: 24,
        fontWeight: 'bold',
        textAlign: 'center',
        marginBottom: 20,
    },
    status: {
        fontSize: 18,
        marginBottom: 20,
        textAlign: 'center',
    },
    buttonContainer: {
        flexDirection: 'row',
        justifyContent: 'space-around',
        marginBottom: 20,
    },
    button: {
        backgroundColor: '#007AFF',
        padding: 10,
        borderRadius: 5,
        minWidth: 80,
    },
    buttonDisabled: {
        backgroundColor: '#ccc',
    },
    buttonText: {
        color: 'white',
        textAlign: 'center',
        fontWeight: 'bold',
    },
    messagesContainer: {
        flex: 1,
        backgroundColor: '#f9f9f9',
        padding: 10,
        borderRadius: 5,
    },
    message: {
        fontSize: 12,
        marginBottom: 5,
    },
});

export default BrainMusicClient;
```

## Real EEG Device Integration

### Connecting Actual EEG Hardware

```javascript
// Example integration with OpenBCI or similar EEG device
class EEGDeviceIntegration {
    constructor() {
        this.ws = null;
        this.eegDevice = null;
        this.sampleBuffer = [];
    }

    async connectToEEGDevice() {
        // This would connect to actual EEG hardware
        // Example using Web Serial API for USB devices
        if ('serial' in navigator) {
            try {
                const port = await navigator.serial.requestPort();
                await port.open({ baudRate: 115200 });
                
                const reader = port.readable.getReader();
                this.readEEGData(reader);
            } catch (error) {
                console.error('Failed to connect to EEG device:', error);
            }
        }
    }

    async readEEGData(reader) {
        try {
            while (true) {
                const { value, done } = await reader.read();
                if (done) break;
                
                // Parse EEG data from device
                const eegSamples = this.parseEEGData(value);
                this.sampleBuffer.push(...eegSamples);
                
                // Send data in chunks
                if (this.sampleBuffer.length >= 100) {
                    this.sendToBackend(this.sampleBuffer.splice(0, 100));
                }
            }
        } catch (error) {
            console.error('Error reading EEG data:', error);
        }
    }

    parseEEGData(rawData) {
        // Convert raw device data to float array
        // This depends on your specific EEG device protocol
        return Array.from(rawData, byte => (byte - 128) / 128.0);
    }

    sendToBackend(samples) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const eegPacket = {
                timestamp: Date.now(),
                signalData: samples
            };
            this.ws.send(JSON.stringify(eegPacket));
        }
    }

    connectToBackend() {
        this.ws = new WebSocket('ws://localhost:8080/ws/brain-waves');
        
        this.ws.onopen = () => {
            console.log('Connected to brain-music backend');
        };
        
        this.ws.onmessage = (event) => {
            const response = JSON.parse(event.data);
            if (response.status === 'success') {
                this.playGeneratedMusic(response.audioBase64);
            }
        };
    }

    playGeneratedMusic(audioBase64) {
        // Play the generated music
        const audio = new Audio(`data:audio/wav;base64,${audioBase64}`);
        audio.play();
    }
}

// Usage
const eegIntegration = new EEGDeviceIntegration();
eegIntegration.connectToBackend();
eegIntegration.connectToEEGDevice();
```

## Key Integration Points

### 1. WebSocket Connection
- **URL**: `ws://localhost:8080/ws/brain-waves`
- **Protocol**: Standard WebSocket
- **CORS**: Enabled for all origins

### 2. Message Format
```javascript
// Outgoing (Frontend → Backend)
{
  "timestamp": 1703123456789,
  "signalData": [1.5, 2.3, -0.8, ...]
}

// Incoming (Backend → Frontend)
{
  "audioBase64": "dGVzdCBhdWRpbyBkYXRh",
  "status": "success|error",
  "message": "Description"
}
```

### 3. Connection Lifecycle
1. **Connect**: Establish WebSocket connection
2. **Send Data**: Stream EEG samples continuously
3. **Receive Music**: Get generated audio when conditions are met
4. **Disconnect**: Clean up resources automatically

### 4. Data Flow Requirements
- **Minimum for generation**: 1280 samples
- **Buffer limit**: 2000 samples per session
- **Cooldown**: 30 seconds between generations
- **Sample rate**: Flexible (depends on your EEG device)

## Production Considerations

### Security
```javascript
// Use WSS in production
const ws = new WebSocket('wss://your-domain.com/ws/brain-waves');

// Add authentication if needed
const ws = new WebSocket('wss://your-domain.com/ws/brain-waves', [], {
    headers: {
        'Authorization': 'Bearer your-jwt-token'
    }
});
```

### Error Handling
```javascript
// Implement reconnection logic
class RobustWebSocketClient {
    constructor(url) {
        this.url = url;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectInterval = 1000;
    }

    connect() {
        this.ws = new WebSocket(this.url);
        
        this.ws.onopen = () => {
            this.reconnectAttempts = 0;
            console.log('Connected');
        };
        
        this.ws.onclose = () => {
            this.attemptReconnect();
        };
        
        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            setTimeout(() => {
                console.log(`Reconnection attempt ${this.reconnectAttempts}`);
                this.connect();
            }, this.reconnectInterval * this.reconnectAttempts);
        }
    }
}
```

This comprehensive guide shows exactly how frontend applications can integrate with your brain-music streaming backend, from simple web pages to complex mobile applications and real EEG device integrations.