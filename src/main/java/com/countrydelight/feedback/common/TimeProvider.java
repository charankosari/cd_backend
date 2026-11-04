package com.countrydelight.feedback.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Single source of "today". All business logic reads the date from here so that testers can
 * move the clock forward (POST /api/admin/clock) to check the X-day and frequency-cap rules
 * without waiting real days.
 */
@Component
public class TimeProvider {

    private final ZoneId zone;
    private volatile LocalDate overrideDate;

    public TimeProvider(@Value("${app.zone:Asia/Kolkata}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        LocalDate o = overrideDate;
        return o != null ? o : LocalDate.now(zone);
    }

    public Instant now() {
        LocalDate o = overrideDate;
        if (o == null) {
            return Instant.now();
        }
        return LocalDateTime.of(o, LocalTime.now(zone)).atZone(zone).toInstant();
    }

    public Instant startOf(LocalDate date) {
        return date.atTime(9, 0).atZone(zone).toInstant();
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean isOverridden() {
        return overrideDate != null;
    }

    public void setToday(LocalDate date) {
        this.overrideDate = date;
    }

    public void reset() {
        this.overrideDate = null;
    }
}
