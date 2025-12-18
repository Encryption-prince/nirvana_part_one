package com.backend.nirvana.dto;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **Feature: brain-music-streaming, Property 9: Music response format and delivery**
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**
 */
class MusicResponsePropertyTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Property(tries = 100)
    void successfulMusicResponseShouldHaveCorrectFormat(
            @ForAll @NotBlank String audioBase64) {
        
        // Create successful music response
        MusicResponse response = MusicResponse.success(audioBase64);
        
        // Validate the response
        Set<ConstraintViolation<MusicResponse>> violations = validator.validate(response);
        
        // Property: Successful response should pass validation
        assertThat(violations).isEmpty();
        
        // Property: Successful response should have all required fields
        assertThat(response.getAudioBase64()).isEqualTo(audioBase64);
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Music generated successfully");
        assertThat(response.getStatus()).isNotNull();
    }

    @Property(tries = 100)
    void errorMusicResponseShouldHaveCorrectFormat(
            @ForAll @NotBlank String errorMessage) {
        
        // Create error music response
        MusicResponse response = MusicResponse.error(errorMessage);
        
        // Validate the response
        Set<ConstraintViolation<MusicResponse>> violations = validator.validate(response);
        
        // Property: Error response should pass validation
        assertThat(violations).isEmpty();
        
        // Property: Error response should have correct format
        assertThat(response.getAudioBase64()).isNull();
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getMessage()).isEqualTo(errorMessage);
        assertThat(response.getStatus()).isNotNull();
    }

    @Property(tries = 100)
    void musicResponseWithNullStatusShouldFailValidation(
            @ForAll String audioBase64,
            @ForAll String message) {
        
        // Create music response with null status
        MusicResponse response = new MusicResponse(audioBase64, null, message);
        
        // Validate the response
        Set<ConstraintViolation<MusicResponse>> violations = validator.validate(response);
        
        // Property: Null status should cause validation failure
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Status cannot be null")))
                .isTrue();
    }

    @Property(tries = 100)
    void musicResponseShouldContainAllRequiredFields(
            @ForAll String audioBase64,
            @ForAll @NotBlank String status,
            @ForAll String message) {
        
        // Create music response with all fields
        MusicResponse response = new MusicResponse(audioBase64, status, message);
        
        // Property: Response should contain all specified fields
        assertThat(response.getAudioBase64()).isEqualTo(audioBase64);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getMessage()).isEqualTo(message);
        
        // Property: Response should be serializable (has getters)
        assertThat(response.toString()).contains(status);
    }
}