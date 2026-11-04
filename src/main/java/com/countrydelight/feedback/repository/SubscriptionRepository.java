package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByCustomerId(Long customerId);

    List<Subscription> findByCustomerIdAndStatus(Long customerId, SubscriptionStatus status);

    List<Subscription> findByStatus(SubscriptionStatus status);

    default boolean isSubscribed(Long customerId, String productId, LocalDate on) {
        return findByCustomerId(customerId).stream()
                .anyMatch(s -> s.getProductId().equals(productId) && s.coversDate(on));
    }
}
