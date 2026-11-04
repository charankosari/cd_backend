package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final SubscriptionDeliveryService subscriptionDeliveries;

    public DeliveryService(OrderRepository orders, TimeProvider time, SubscriptionDeliveryService subscriptionDeliveries) {
        this.orders = orders;
        this.time = time;
        this.subscriptionDeliveries = subscriptionDeliveries;
    }

    @Transactional
    public int markDueOrdersDelivered(Long customerId) {
        subscriptionDeliveries.generateFor(customerId);          // today's subscription deliveries first
        List<CustomerOrder> due = orders.findByCustomerIdAndStatusAndDeliveryDateLessThanEqual(
                customerId, OrderStatus.SCHEDULED, time.today());
        due.forEach(CustomerOrder::markDelivered);
        return due.size();
    }

    /** Same as above, for every customer (before the dashboard lists orders). */
    @Transactional
    public int markAllDueOrdersDelivered() {
        subscriptionDeliveries.generateAll();
        List<CustomerOrder> due = orders.findByStatusAndDeliveryDateLessThanEqual(OrderStatus.SCHEDULED, time.today());
        due.forEach(CustomerOrder::markDelivered);
        return due.size();
    }

    /**
     * "Mark delivered" from the dashboard: deliver now. If the order was planned for a later date, its delivery
     * date becomes today, because that's when the customer actually received it. The X-day feedback wait then
     * counts from today, and "Rate now" works immediately.
     */
    @Transactional
    public CustomerOrder deliverNow(Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order " + orderId));
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw ApiException.conflict("ALREADY_DELIVERED", "Order #" + orderId + " is already delivered");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.conflict("ORDER_CANCELLED", "Order #" + orderId + " was cancelled");
        }
        LocalDate today = time.today();
        order.markDeliveredOn(order.getDeliveryDate().isAfter(today) ? today : order.getDeliveryDate());
        return order;
    }
}
