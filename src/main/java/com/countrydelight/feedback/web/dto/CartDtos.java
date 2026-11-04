package com.countrydelight.feedback.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }
    public record AddToCartRequest(@NotBlank String productId,
                                   @Min(1) @Max(20) Integer quantity,
                                   LocalDate deliveryDate,
                                   Long feedbackId) {
    }

    public record CartItemResponse(Long cartItemId,
                                   String productId,
                                   String productName,
                                   String unit,
                                   BigDecimal unitPrice,
                                   int quantity,
                                   BigDecimal lineTotal,
                                   LocalDate deliveryDate,
                                   String source,
                                   Long feedbackId,
                                   String imageUrl) {
    }

    public record CartResponse(Long customerId, List<CartItemResponse> items, int totalQuantity, BigDecimal totalAmount) {
    }

    public record AddToCartResponse(CartItemResponse item, String message, CartResponse cart) {
    }
}
