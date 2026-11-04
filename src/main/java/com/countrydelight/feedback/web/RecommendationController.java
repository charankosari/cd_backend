package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.service.RecommendationService;
import com.countrydelight.feedback.web.dto.RecommendationDtos.BuyAgainResponse;
import com.countrydelight.feedback.web.dto.RecommendationDtos.CartSuggestionsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping("/buy-again")
    public BuyAgainResponse buyAgain(@CurrentCustomer Long customerId) {
        return recommendations.buyAgain(customerId);
    }

    @GetMapping("/for-cart")
    public CartSuggestionsResponse forCart(@CurrentCustomer Long customerId) {
        return recommendations.forCart(customerId);
    }
}
