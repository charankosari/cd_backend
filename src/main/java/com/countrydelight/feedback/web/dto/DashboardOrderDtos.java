package com.countrydelight.feedback.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Orders screen of the quality/ops dashboard. */
public final class DashboardOrderDtos {

    private DashboardOrderDtos() {
    }

    /** GET /api/dashboard/orders */
    public record OrdersPage(List<OrderRow> content,
                             int page,
                             int size,
                             long totalElements,
                             int totalPages,
                             Map<String, Long> counts) {   // SCHEDULED / DELIVERED / CANCELLED / ALL
    }

    public record OrderRow(Long orderId,
                           Long customerId,
                           String customerName,
                           String area,
                           String city,
                           LocalDate deliveryDate,
                           String status,                // SCHEDULED | DELIVERED | CANCELLED
                           boolean canMarkDelivered,     // true -> show the "Mark delivered" button
                           int totalQuantity,
                           BigDecimal totalAmount,
                           List<OrderLine> items,
                           String deliveryAddress) {
    }

    public record OrderLine(Long orderItemId,
                            String productId,
                            String productName,
                            String unit,
                            int quantity,
                            BigDecimal lineTotal,
                            String purchaseType,          // ONE_TIME | SUBSCRIPTION
                            String imageUrl) {
    }

    /** POST /api/dashboard/orders/{orderId}/deliver */
    public record DeliverResponse(OrderRow order, String message) {
    }

    /** GET /api/dashboard/carts: what customers have in their carts right now (not yet ordered). */
    public record CartsOverview(List<CustomerCart> carts,
                                int customers,
                                int lines,
                                int totalQuantity,
                                BigDecimal totalValue) {
    }

    public record CustomerCart(Long customerId,
                               String customerName,
                               String area,
                               String city,
                               int totalQuantity,
                               BigDecimal totalAmount,
                               java.time.Instant lastAddedAt,
                               List<CartLine> items) {
    }

    public record CartLine(Long cartItemId,
                           String productId,
                           String productName,
                           String unit,
                           int quantity,
                           BigDecimal lineTotal,
                           LocalDate deliveryDate,
                           String source,          // MANUAL | FEEDBACK_BUY_AGAIN (added from the buy-again nudge)
                           String imageUrl) {
    }
}
