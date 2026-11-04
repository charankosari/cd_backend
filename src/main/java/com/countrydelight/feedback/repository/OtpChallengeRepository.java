package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    /** The newest code for this phone that hasn't been used yet. */
    Optional<OtpChallenge> findFirstByPhoneAndConsumedAtIsNullOrderByIdDesc(String phone);

    /** The newest code for this phone, used or not (for the resend cooldown). */
    Optional<OtpChallenge> findFirstByPhoneOrderByIdDesc(String phone);

    /** All unused codes for this phone (to cancel them when a new one is sent). */
    List<OtpChallenge> findByPhoneAndConsumedAtIsNull(String phone);

    /** How many codes this phone asked for since a moment in time (for the hourly limit). */
    long countByPhoneAndCreatedAtAfter(String phone, Instant since);
}
