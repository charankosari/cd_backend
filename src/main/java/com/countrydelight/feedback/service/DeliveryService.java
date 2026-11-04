package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stand-in for the real delivery system. Country Delight delivers in the morning, so a SCHEDULED order
 * counts as DELIVERED once its delivery date has arrived. It runs just before we read a customer's orders
 * or decide on a feedback card, so it also follows the demo clock (/api/admin/clock).
 * In production, the delivery app would set DELIVERED instead.
 */
@Service
public class DeliveryService {

    private final OrderRepository orders;
    private final TimeProvider time;

    public DeliveryService(OrderRepository orders, TimeProvider time) {
        this.orders = orders;
        this.time = time;
    }

    @Transactional
    public int markDueOrdersDelivered(Long customerId) {
        List<CustomerOrder> due = orders.findByCustomerIdAndStatusAndDeliveryDateLessThanEqual(
                customerId, OrderStatus.SCHEDULED, time.today());
        due.forEach(CustomerOrder::markDelivered);
        return due.size();
    }
}
