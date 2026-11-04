package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCustomerIdOrderByDeliveryDateAscIdAsc(Long customerId);

    List<CartItem> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    Optional<CartItem> findFirstByCustomerIdAndProductIdAndDeliveryDate(Long customerId, String productId, LocalDate date);

    long countByFeedbackIdIn(Collection<Long> feedbackIds);

    List<CartItem> findByDeliveryDateAfterOrderByCustomerIdAscDeliveryDateAscIdAsc(LocalDate date);
}
