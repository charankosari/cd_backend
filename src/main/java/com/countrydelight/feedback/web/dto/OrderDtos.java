package com.countrydelight.feedback.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(LocalDate deliveryDate, Long addressId) {
    }

    public record PlaceOrderResponse(List<OrderView> orders, String message) {
    }

    public record OrderView(Long orderId,
                            LocalDate deliveryDate,
                            String status,
                            String area,
                            String city,
                            int totalQuantity,
                            BigDecimal totalAmount,
                            List<OrderItemView> items,
                            Long addressId,
                            String deliveryAddress,
                            java.time.Instant placedAt) {
    }

    public record OrderItemView(Long orderItemId,
                                String productId,
                                String productName,
                                String unit,
                                int quantity,
                                BigDecimal unitPrice,
                                BigDecimal lineTotal,
                                String purchaseType,
                                ItemFeedback feedback,
                                String imageUrl) {
    }

    public record ItemFeedback(String status,
                               boolean canRateNow,
                               LocalDate autoAskOn,
                               Integer rating,
                               Long feedbackId) {
    }
}
