package com.countrydelight.feedback.domain;

public enum PromptReason {
    NEW_TO_HOUSEHOLD(50, "First time this household bought this product"),
    FIRST_TIME_CUSTOMER(30, "Household's first order with us"),
    ONE_TIME_PURCHASE(10, "One-time (non-subscription) purchase"),
    USER_REQUESTED(0, "User tapped \"Rate now\" on the order");

    private final int weight;
    private final String description;

    PromptReason(int weight, String description) {
        this.weight = weight;
        this.description = description;
    }

    public int weight() {
        return weight;
    }

    public String description() {
        return description;
    }
}
