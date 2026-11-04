package com.countrydelight.feedback.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DashboardOrderDtos {

    private DashboardOrderDtos() {
    }

    public record OrdersPage(List<OrderRow> content,
                             int page,
                             int size,
                             long totalElements,
                             int totalPages,
                             Map<String, Long> counts) {
    }

    public record OrderRow(Long orderId,
                           Long customerId,
                           String customerName,
                           String area,
                           String city,
                           LocalDate deliveryDate,
                           String status,
                           boolean canMarkDelivered,
                           int totalQuantity,
                           BigDecimal totalAmount,
                           List<OrderLine> items,
                           String deliveryAddress,
                           String orderType,
                           java.time.Instant placedAt) {
    }

    public record OrderLine(Long orderItemId,
                            String productId,
                            String productName,
                            String unit,
                            int quantity,
                            BigDecimal lineTotal,
                            String purchaseType,
                            String imageUrl) {
    }

    public record DeliverResponse(OrderRow order, String message) {
    }

    public record SubscriptionsPage(List<SubscriptionRow> content,
                                    Map<String, Long> counts,
                                    BigDecimal activeMonthlyValue) {
    }

    public record SubscriptionRow(Long subscriptionId,
                                  Long customerId,
                                  String customerName,
                                  String productId,
                                  String productName,
                                  String unit,
                                  String imageUrl,
                                  int quantity,
                                  String frequency,
                                  String status,
                                  LocalDate startDate,
                                  LocalDate nextDeliveryDate,
                                  LocalDate endDate,
                                  String deliveryAddress,
                                  BigDecimal estimatedMonthlyCost) {
    }

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
                           String source,
                           String imageUrl) {
    }
}
