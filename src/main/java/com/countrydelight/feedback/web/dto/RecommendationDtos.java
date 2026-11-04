package com.countrydelight.feedback.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /** GET /api/recommendations/buy-again */
    public record BuyAgainResponse(List<BuyAgainItem> items, List<ExcludedProduct> excluded) {
    }

    /** A product the user rated 4-5★: show an "Order again" button (add to cart with feedbackId). */
    public record BuyAgainItem(String productId,
                               String productName,
                               String unit,
                               BigDecimal price,
                               String imageUrl,
                               int rating,
                               LocalDate ratedOn,
                               Long feedbackId,          // pass to POST /api/cart/items
                               String reason,            // e.g. "You rated it 5★"
                               LocalDate suggestedDate,  // tomorrow
                               String addToCartPath) {
    }

    /** GET /api/recommendations/for-cart */
    public record CartSuggestionsResponse(List<CartSuggestion> items, List<ExcludedProduct> excluded) {
    }

    /** "Goes well with your cart": add it for the same delivery date as the item it pairs with. */
    public record CartSuggestion(String productId,
                                 String productName,
                                 String unit,
                                 BigDecimal price,
                                 String imageUrl,
                                 List<String> pairsWith,        // productIds in the cart it goes with
                                 Integer yourRating,           // set when the user rated it 4-5★
                                 String reason,                // e.g. "Goes well with Whole Wheat Bread"
                                 LocalDate suggestedDate,      // delivery date of that cart line
                                 String addToCartPath) {
    }

    /**
     * A product the user rated, but we deliberately don't recommend. For transparency / testing only;
     * the app shows nothing for these. reason: RATED_LOW | SUBSCRIBED | IN_CART. rating is null when not rated.
     */
    public record ExcludedProduct(String productId, String productName, Integer rating, String reason) {
    }
}
