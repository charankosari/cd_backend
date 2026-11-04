package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.PurchaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByCustomerIdOrderByPlacedAtDescIdDesc(Long customerId);

    List<CustomerOrder> findByCustomerIdAndStatusAndDeliveryDateLessThanEqual(Long customerId, OrderStatus status,
                                                                              LocalDate date);

    List<CustomerOrder> findByStatusAndDeliveryDateLessThanEqual(OrderStatus status, LocalDate date);

    @Query("select o from CustomerOrder o where (:status is null or o.status = :status) and (:type is null or exists "
            + "(select i.id from OrderItem i where i.order = o and i.purchaseType = :type))")
    Page<CustomerOrder> search(@Param("status") OrderStatus status, @Param("type") PurchaseType type, Pageable pageable);

    @Query("select count(o) from CustomerOrder o where exists "
            + "(select i.id from OrderItem i where i.order = o and i.purchaseType = :type)")
    long countContaining(@Param("type") PurchaseType type);

    long countByStatus(OrderStatus status);
}
