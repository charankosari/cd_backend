package com.countrydelight.feedback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;


@ConfigurationProperties(prefix = "feedback")
public record FeedbackProperties(
        @DefaultValue("3") int windowDays,
        @DefaultValue("3") int userCooldownDays,
        @DefaultValue("3") int maxImpressionDays,
        @DefaultValue("30") int productCooldownAfterAnswerDays,
        @DefaultValue("60") int productCooldownAfterSkipDays,
        @DefaultValue("4") int highRatingThreshold,
        @DefaultValue("200") int noteMaxLength,
        @DefaultValue("30") int buyAgainMaxDaysAhead) {
}
