package com.countrydelight.feedback.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under "app.auth" in application.yml. */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Jwt jwt, Otp otp) {

    public record Jwt(String secret, long ttlHours, String issuer) {
    }

    public record Otp(String fixedCode, int length, long ttlSeconds, long resendCooldownSeconds,
                      int maxAttempts, int maxRequestsPerHour) {

        public boolean usesFixedCode() {
            return fixedCode != null && !fixedCode.isBlank();
        }
    }
}
