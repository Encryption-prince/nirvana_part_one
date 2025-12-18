package com.backend.nirvana.dto;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import net.jqwik.api.constraints.Positive;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **Feature: brain-music-streaming, Property 4: EEG data parsing and validation**
 * **Validates: Requirements 2.1, 2.2, 2.3**
 */
class EEGDataPacketPropertyTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Property(tries = 100)
    void validEEGDataPacketShouldPassValidation(
            @ForAll @Positive Long timestamp,
            @ForAll @NotEmpty List<Float> signalData) {
        
        // Create EEG data packet with valid data
        EEGDataPacket packet = new EEGDataPacket(timestamp, signalData);
        
        // Validate the packet
        Set<ConstraintViolation<EEGDataPacket>> violations = validator.validate(packet);
        
        // Property: Valid EEG data should pass validation
        assertThat(violations).isEmpty();
        
        // Property: Parsed data should match input data
        assertThat(packet.getTimestamp()).isEqualTo(timestamp);
        assertThat(packet.getSignalData()).isEqualTo(signalData);
        assertThat(packet.getSignalData().size()).isEqualTo(signalData.size());
    }

    @Property(tries = 100)
    void nullTimestampShouldFailValidation(@ForAll @NotEmpty List<Float> signalData) {
        
        // Create EEG data packet with null timestamp
        EEGDataPacket packet = new EEGDataPacket(null, signalData);
        
        // Validate the packet
        Set<ConstraintViolation<EEGDataPacket>> violations = validator.validate(packet);
        
        // Property: Null timestamp should cause validation failure
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Timestamp cannot be null")))
                .isTrue();
    }

    @Property(tries = 100)
    void nullSignalDataShouldFailValidation(@ForAll @Positive Long timestamp) {
        
        // Create EEG data packet with null signal data
        EEGDataPacket packet = new EEGDataPacket(timestamp, null);
        
        // Validate the packet
        Set<ConstraintViolation<EEGDataPacket>> violations = validator.validate(packet);
        
        // Property: Null signal data should cause validation failure
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Signal data cannot be null")))
                .isTrue();
    }

    @Property(tries = 100)
    void emptySignalDataShouldFailValidation(@ForAll @Positive Long timestamp) {
        
        // Create EEG data packet with empty signal data
        EEGDataPacket packet = new EEGDataPacket(timestamp, List.of());
        
        // Validate the packet
        Set<ConstraintViolation<EEGDataPacket>> violations = validator.validate(packet);
        
        // Property: Empty signal data should cause validation failure
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Signal data cannot be empty")))
                .isTrue();
    }
}