package com.countrydelight.feedback.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record BuyAgainResponse(List<BuyAgainItem> items, List<ExcludedProduct> excluded) {
    }

    public record BuyAgainItem(String productId,
                               String productName,
                               String unit,
                               BigDecimal price,
                               String imageUrl,
                               int rating,
                               LocalDate ratedOn,
                               Long feedbackId,
                               String reason,
                               LocalDate suggestedDate,
                               String addToCartPath) {
    }

    public record CartSuggestionsResponse(List<CartSuggestion> items, List<ExcludedProduct> excluded) {
    }

    public record CartSuggestion(String productId,
                                 String productName,
                                 String unit,
                                 BigDecimal price,
                                 String imageUrl,
                                 List<String> pairsWith,
                                 Integer yourRating,
                                 String reason,
                                 LocalDate suggestedDate,
                                 String addToCartPath) {
    }

    public record ExcludedProduct(String productId, String productName, Integer rating, String reason) {
    }
}
