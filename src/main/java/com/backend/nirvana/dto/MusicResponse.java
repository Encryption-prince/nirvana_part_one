package com.backend.nirvana.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MusicResponse {
    
    private String audioBase64;
    
    @NotNull(message = "Status cannot be null")
    private String status;
    
    private String message;
    
    // Convenience constructors for different response types
    public static MusicResponse success(String audioBase64) {
        return new MusicResponse(audioBase64, "success", "Music generated successfully");
    }
    
    public static MusicResponse error(String message) {
        return new MusicResponse(null, "error", message);
    }
}