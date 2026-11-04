package com.countrydelight.feedback.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record OtpRequest(@NotBlank String phone) {
    }

    public record OtpRequestResponse(String phone,
                                     boolean registered,
                                     int otpLength,
                                     long expiresInSeconds,
                                     long resendAfterSeconds,
                                     String message) {
    }

    public record LoginRequest(@NotBlank String phone,
                               @NotBlank @Pattern(regexp = "\\d{4,8}", message = "must be digits") String otp) {
    }

    public record RegisterRequest(@NotBlank String phone,
                                  @NotBlank @Pattern(regexp = "\\d{4,8}", message = "must be digits") String otp,
                                  @NotBlank @Size(max = 80) String name,
                                  @NotBlank @Size(max = 80) String area,
                                  @NotBlank @Size(max = 60) String city) {
    }

    public record AuthResponse(String accessToken,
                               String tokenType,
                               long expiresInSeconds,
                               Instant expiresAt,
                               boolean newUser,
                               CustomerProfile customer) {
    }

    public record CustomerProfile(Long customerId, String name, String phone, String area, String city) {
    }
}
