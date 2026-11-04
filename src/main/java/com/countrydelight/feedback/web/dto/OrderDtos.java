package com.countrydelight.feedback.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    /** POST /api/orders. Body optional; deliveryDate = only order the cart lines for that date. */
    public record PlaceOrderRequest(LocalDate deliveryDate, Long addressId) {
    }

    public record PlaceOrderResponse(List<OrderView> orders, String message) {
    }

    public record OrderView(Long orderId,
                            LocalDate deliveryDate,
                            String status,          // SCHEDULED | DELIVERED | CANCELLED
                            String area,
                            String city,
                            int totalQuantity,
                            BigDecimal totalAmount,
                            List<OrderItemView> items,
                            Long addressId,
                            String deliveryAddress) {    // full address text it was / will be delivered to
    }

    public record OrderItemView(Long orderItemId,
                                String productId,
                                String productName,
                                String unit,
                                int quantity,
                                BigDecimal unitPrice,
                                BigDecimal lineTotal,
                                String purchaseType,   // ONE_TIME | SUBSCRIPTION
                                ItemFeedback feedback,
                                String imageUrl) {
    }

    /**
     * Feedback state of one delivered item, so the orders screen can show "Rate now" or "Rated 4★".
     * status: NOT_DELIVERED | NOT_RATED | RATED | ALREADY_REVIEWED (product reviewed on an earlier order; rating and
     * feedbackId are that earlier review) | NOT_AVAILABLE (product has no feedback)
     */
    public record ItemFeedback(String status,
                               boolean canRateNow,
                               LocalDate autoAskOn,   // when the home-screen card would ask by itself
                               Integer rating,
                               Long feedbackId) {
    }
}
