package com.countrydelight.feedback.web.dto;

import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }
    public record IssueDetailsRequest(@NotEmpty List<String> issueCodes, String note, String photoUrl) {
    }

    public record FeedbackDetailResponse(Long feedbackId,
                                         Long promptId,
                                         Long customerId,
                                         String productId,
                                         String productName,
                                         int rating,
                                         List<IssueOptionDto> issues,
                                         String note,
                                         String photoUrl,
                                         Long orderId,
                                         Long orderItemId,
                                         LocalDate deliveryDate,
                                         String area,
                                         String city,
                                         Instant createdAt,
                                         String message) {
    }

    public record PhotoUploadResponse(Long feedbackId, String photoUrl) {
    }
}
