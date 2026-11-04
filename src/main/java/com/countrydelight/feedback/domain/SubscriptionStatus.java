package com.countrydelight.feedback.domain;

public enum SubscriptionStatus {
    ACTIVE,
    /** Paused subscribers still count as "subscribed": they know the product, so no feedback ask. */
    PAUSED,
    CANCELLED
}
