package com.countrydelight.feedback.auth;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.domain.OtpChallenge;
import com.countrydelight.feedback.repository.OtpChallengeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class OtpService {

    public record OtpIssued(String phone, long expiresInSeconds, long resendAfterSeconds) {
    }

    private final OtpChallengeRepository otps;
    private final OtpCodeGenerator generator;
    private final OtpSender sender;
    private final PasswordEncoder hasher;
    private final AuthProperties.Otp config;

    public OtpService(OtpChallengeRepository otps, OtpCodeGenerator generator, OtpSender sender,
                      PasswordEncoder hasher, AuthProperties props) {
        this.otps = otps;
        this.generator = generator;
        this.sender = sender;
        this.hasher = hasher;
        this.config = props.otp();
    }

    @Transactional
    public OtpIssued request(String phone) {
        Instant now = Instant.now();

        otps.findFirstByPhoneOrderByIdDesc(phone).ifPresent(last -> {
            long wait = Duration.between(now, last.getCreatedAt().plusSeconds(config.resendCooldownSeconds())).toSeconds();
            if (wait > 0) {
                throw ApiException.tooManyRequests("OTP_RESEND_TOO_SOON",
                        "Please wait " + wait + " seconds before asking for a new OTP");
            }
        });
        if (otps.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= config.maxRequestsPerHour()) {
            throw ApiException.tooManyRequests("OTP_LIMIT_REACHED", "Too many OTP requests. Try again in an hour");
        }

        otps.findByPhoneAndConsumedAtIsNull(phone).forEach(old -> old.consume(now));
        String code = generator.newCode();
        otps.save(new OtpChallenge(phone, hasher.encode(code), now, now.plusSeconds(config.ttlSeconds())));
        sender.send(phone, code);
        return new OtpIssued(phone, config.ttlSeconds(), config.resendCooldownSeconds());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(String phone, String code) {
        Instant now = Instant.now();
        OtpChallenge challenge = otps.findFirstByPhoneAndConsumedAtIsNullOrderByIdDesc(phone)
                .orElseThrow(() -> ApiException.badRequest("OTP_NOT_REQUESTED", "Request an OTP first"));

        if (challenge.isExpired(now)) {
            challenge.consume(now);
            throw ApiException.badRequest("OTP_EXPIRED", "OTP expired. Request a new one");
        }
        if (challenge.getAttempts() >= config.maxAttempts()) {
            challenge.consume(now);
            throw ApiException.tooManyRequests("OTP_TOO_MANY_ATTEMPTS", "Too many wrong attempts. Request a new OTP");
        }
        if (code == null || !hasher.matches(code.trim(), challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            int left = config.maxAttempts() - challenge.getAttempts();
            if (left <= 0) {
                challenge.consume(now);
                throw ApiException.tooManyRequests("OTP_TOO_MANY_ATTEMPTS", "Too many wrong attempts. Request a new OTP");
            }
            throw ApiException.badRequest("OTP_INVALID", "Wrong OTP. " + left + " attempt(s) left");
        }
        challenge.consume(now);
    }
}
