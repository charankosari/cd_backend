package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select i from OrderItem i join fetch i.order o "
            + "where o.customerId = :customerId and o.status = :status "
            + "order by o.deliveryDate asc, i.id asc")
    List<OrderItem> findByCustomerAndStatus(@Param("customerId") Long customerId,
                                            @Param("status") OrderStatus status);

    default List<OrderItem> findDeliveredByCustomer(Long customerId) {
        return findByCustomerAndStatus(customerId, OrderStatus.DELIVERED);
    }

    @Query("select i from OrderItem i join fetch i.order where i.id = :id")
    Optional<OrderItem> findWithOrder(@Param("id") Long id);
}
