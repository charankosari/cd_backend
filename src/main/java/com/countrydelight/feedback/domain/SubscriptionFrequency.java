package com.countrydelight.feedback.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** How often a subscription delivers. */
public enum SubscriptionFrequency {
    /** Every morning. */
    DAILY,
    /** Every second day, counted from the start date. */
    ALTERNATE_DAYS;

    public boolean deliversOn(LocalDate startDate, LocalDate date) {
        if (date.isBefore(startDate)) {
            return false;
        }
        return this == DAILY || ChronoUnit.DAYS.between(startDate, date) % 2 == 0;
    }

    /** Roughly how many deliveries in 30 days (for the monthly estimate). */
    public int deliveriesPerMonth() {
        return this == DAILY ? 30 : 15;
    }
}
