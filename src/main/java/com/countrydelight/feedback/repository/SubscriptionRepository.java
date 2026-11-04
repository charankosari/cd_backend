package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByCustomerId(Long customerId);

    default boolean isSubscribed(Long customerId, String productId, LocalDate on) {
        return findByCustomerId(customerId).stream()
                .anyMatch(s -> s.getProductId().equals(productId) && s.coversDate(on));
    }
}
