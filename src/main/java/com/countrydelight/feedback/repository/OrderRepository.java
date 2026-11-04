package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** A customer's orders, newest delivery first. */
    List<CustomerOrder> findByCustomerIdOrderByDeliveryDateDescIdDesc(Long customerId);

    /** Scheduled orders whose delivery day has come (to mark them delivered). */
    List<CustomerOrder> findByCustomerIdAndStatusAndDeliveryDateLessThanEqual(Long customerId, OrderStatus status,
                                                                              LocalDate date);

    /** Same, for all customers (dashboard orders screen). */
    List<CustomerOrder> findByStatusAndDeliveryDateLessThanEqual(OrderStatus status, LocalDate date);

    /** Dashboard: one status, one page. */
    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);
}
