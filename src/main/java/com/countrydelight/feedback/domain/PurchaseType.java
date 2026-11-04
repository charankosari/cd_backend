package com.countrydelight.feedback.domain;

public enum PurchaseType {
    /** Bought once (cart / "buy once"). Eligible for feedback. */
    ONE_TIME,
    /** Delivered as part of a recurring subscription. Never asked for feedback. */
    SUBSCRIPTION
}
