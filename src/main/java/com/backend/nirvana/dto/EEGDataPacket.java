package com.backend.nirvana.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EEGDataPacket {
    
    @NotNull(message = "Timestamp cannot be null")
    private Long timestamp;
    
    @NotNull(message = "Signal data cannot be null")
    @NotEmpty(message = "Signal data cannot be empty")
    private List<Float> signalData;
}