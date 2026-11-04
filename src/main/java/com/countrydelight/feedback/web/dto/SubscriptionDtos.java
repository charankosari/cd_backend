package com.countrydelight.feedback.web.dto;

import com.countrydelight.feedback.domain.SubscriptionFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record SubscribeRequest(@NotBlank String productId,
                                   @Min(1) @Max(10) Integer quantity,
                                   SubscriptionFrequency frequency,
                                   LocalDate startDate,
                                   Long addressId) {
    }

    public record UpdateSubscriptionRequest(@Min(1) @Max(10) Integer quantity,
                                            SubscriptionFrequency frequency,
                                            Long addressId) {
    }

    public record SubscriptionView(Long subscriptionId,
                                   String productId,
                                   String productName,
                                   String unit,
                                   BigDecimal price,
                                   String imageUrl,
                                   int quantity,
                                   String frequency,
                                   String status,
                                   LocalDate startDate,
                                   LocalDate nextDeliveryDate,
                                   LocalDate endDate,
                                   Long addressId,
                                   String deliveryAddress,
                                   BigDecimal estimatedMonthlyCost,
                                   String message) {
    }

    public record SubscriptionList(List<SubscriptionView> subscriptions, int activeCount, BigDecimal estimatedMonthlyTotal) {
    }
}
