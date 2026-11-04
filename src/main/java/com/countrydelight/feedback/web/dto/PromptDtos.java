package com.countrydelight.feedback.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PromptDtos {

    private PromptDtos() {
    }

    public record PromptResponse(boolean show, PromptCard prompt, String reasonCode, String message,
                                 LocalDate nextEligibleOn) {

        public static PromptResponse shown(PromptCard card) {
            return new PromptResponse(true, card, null, null, null);
        }

        public static PromptResponse hidden(String reasonCode, String message, LocalDate nextEligibleOn) {
            return new PromptResponse(false, null, reasonCode, message, nextEligibleOn);
        }
    }

    public record PromptCard(Long promptId,
                             String productId,
                             String productName,
                             String productUnit,
                             String imageUrl,
                             String title,
                             String subtitle,
                             Long orderId,
                             LocalDate deliveryDate,
                             LocalDate expiresOn,
                             List<String> reasons,
                             int ratingMin,
                             int ratingMax,
                             int highRatingThreshold,
                             String skipLabel) {
    }

    public record RatingRequest(@NotNull @Min(1) @Max(5) Integer rating) {
    }

    public record RatingResponse(Long feedbackId,
                                 Long promptId,
                                 int rating,
                                 String nextStep,
                                 String message,
                                 BuyAgainOffer buyAgain,
                                 IssueForm issueForm) {
    }

    public record BuyAgainOffer(String productId,
                                String productName,
                                String unit,
                                BigDecimal price,
                                int defaultQuantity,
                                LocalDate suggestedDate,
                                LocalDate minDate,
                                LocalDate maxDate,
                                List<QuickDate> quickDates,
                                String addToCartPath) {
    }

    public record QuickDate(String label, LocalDate date) {
    }

    public record IssueForm(String question,
                            List<IssueOptionDto> options,
                            boolean multiSelect,
                            int minSelections,
                            boolean noteAllowed,
                            int noteMaxLength,
                            boolean photoAllowed,
                            String submitPath,
                            String photoUploadPath) {
    }

    public record IssueOptionDto(String code, String label) {
    }

    public record SkipResponse(Long promptId, String status, String message) {
    }
}
