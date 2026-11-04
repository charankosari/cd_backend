package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.FeedbackPrompt;
import com.countrydelight.feedback.domain.PromptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackPromptRepository extends JpaRepository<FeedbackPrompt, Long> {

    Optional<FeedbackPrompt> findFirstByCustomerIdAndStatusOrderByIdDesc(Long customerId, PromptStatus status);

    List<FeedbackPrompt> findByCustomerIdOrderByIdAsc(Long customerId);
}
