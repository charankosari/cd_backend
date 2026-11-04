package com.countrydelight.feedback.domain;

/** Who started a feedback prompt. */
public enum PromptSource {
    /** We asked: the home-screen card after X days. Counts toward the frequency cap. */
    AUTO,
    /** The user tapped "Rate now" on an order, skipping the X-day wait. Doesn't count toward the cap. */
    USER
}
