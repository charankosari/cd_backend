package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One OTP sent to one phone number. We store only a HASH of the code (like a password),
 * so someone reading the database can't log in as the user.
 */
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private int attempts;

    /** Set when the code was used successfully, or replaced by a newer code. A used code can't be reused. */
    private Instant consumedAt;

    protected OtpChallenge() {
    }

    public OtpChallenge(String phone, String codeHash, Instant createdAt, Instant expiresAt) {
        this.phone = phone;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public void consume(Instant at) {
        this.consumedAt = at;
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public Instant getConsumedAt() { return consumedAt; }
}
