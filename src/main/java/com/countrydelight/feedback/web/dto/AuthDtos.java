package com.countrydelight.feedback.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Step 1 (login and signup): POST /api/auth/otp/request */
    public record OtpRequest(@NotBlank String phone) {
    }

    /** registered tells the app which screen comes next: false = signup form, true = just the OTP box. */
    public record OtpRequestResponse(String phone,
                                     boolean registered,
                                     int otpLength,
                                     long expiresInSeconds,
                                     long resendAfterSeconds,
                                     String message) {
    }

    /** Step 2a, existing user: POST /api/auth/login */
    public record LoginRequest(@NotBlank String phone,
                               @NotBlank @Pattern(regexp = "\\d{4,8}", message = "must be digits") String otp) {
    }

    /** Step 2b, new user: POST /api/auth/register (signup and OTP check in one call) */
    public record RegisterRequest(@NotBlank String phone,
                                  @NotBlank @Pattern(regexp = "\\d{4,8}", message = "must be digits") String otp,
                                  @NotBlank @Size(max = 80) String name,
                                  @NotBlank @Size(max = 80) String area,
                                  @NotBlank @Size(max = 60) String city) {
    }

    /** The app stores accessToken and sends it as "Authorization: Bearer <accessToken>" on every call. */
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
