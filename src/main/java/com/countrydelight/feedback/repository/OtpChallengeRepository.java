package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findFirstByPhoneAndConsumedAtIsNullOrderByIdDesc(String phone);

    Optional<OtpChallenge> findFirstByPhoneOrderByIdDesc(String phone);

    List<OtpChallenge> findByPhoneAndConsumedAtIsNull(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);
}
