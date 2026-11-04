package com.countrydelight.feedback.domain;

public enum PromptStatus {
    /** Shown to the user, waiting for a rating or skip. */
    PENDING,
    /** User gave a rating. */
    ANSWERED,
    /** User tapped "Skip". */
    SKIPPED,
    /** Shown on several days (or its window ran out) without any action. */
    IGNORED,
    /** Withdrawn by the system, e.g. the user subscribed to the product in the meantime. */
    CANCELLED
}
