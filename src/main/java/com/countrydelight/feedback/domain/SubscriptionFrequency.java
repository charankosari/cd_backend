package com.countrydelight.feedback.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum SubscriptionFrequency {
    DAILY,
    ALTERNATE_DAYS;

    public boolean deliversOn(LocalDate startDate, LocalDate date) {
        if (date.isBefore(startDate)) {
            return false;
        }
        return this == DAILY || ChronoUnit.DAYS.between(startDate, date) % 2 == 0;
    }

    public int deliveriesPerMonth() {
        return this == DAILY ? 30 : 15;
    }
}
