package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** A customer's orders, newest delivery first. */
    List<CustomerOrder> findByCustomerIdOrderByDeliveryDateDescIdDesc(Long customerId);

    /** Scheduled orders whose delivery day has come (to mark them delivered). */
    List<CustomerOrder> findByCustomerIdAndStatusAndDeliveryDateLessThanEqual(Long customerId, OrderStatus status,
                                                                              LocalDate date);
}
