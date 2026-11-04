package com.countrydelight.feedback.web.dto;

import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;

import java.math.BigDecimal;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductView(String productId,
                              String name,
                              String description,
                              String category,
                              String unit,
                              BigDecimal price,
                              String imageUrl,
                              boolean feedbackEnabled,
                              boolean subscribable,
                              Integer feedbackDelayDays,
                              List<IssueOptionDto> issueOptions) {
    }
}
