package com.countrydelight.feedback.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * "Goes well with" pairs used for cart recommendations. Hand-picked for the demo catalog, best match first.
 * In production this would come from co-purchase data ("people who bought X also bought Y").
 */
@Component
public class ProductPairings {

    private static final Map<String, List<String>> PAIRS = Map.of(
            "BREAD", List.of("BUTTER", "EGGS", "HONEY"),
            "BUTTER", List.of("BREAD", "EGGS"),
            "EGGS", List.of("BREAD", "BUTTER", "MILK"),
            "MILK", List.of("BREAD", "HONEY", "EGGS"),
            "HONEY", List.of("BREAD", "MILK"),
            "PANEER", List.of("DAHI", "GHEE", "ATTA"),
            "DAHI", List.of("PANEER", "ATTA"),
            "ATTA", List.of("GHEE", "DAHI", "PANEER"),
            "GHEE", List.of("ATTA", "HONEY"),
            "COCONUT_WATER", List.of("HONEY", "DAHI"));

    /** Products that go well with this one, best match first (empty if none). */
    public List<String> goesWellWith(String productId) {
        return PAIRS.getOrDefault(productId, List.of());
    }
}
