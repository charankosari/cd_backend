package com.countrydelight.feedback.web.dto;

import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;

import java.math.BigDecimal;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** GET /api/products and /api/products/{productId} */
    public record ProductView(String productId,
                              String name,
                              String description,
                              String category,
                              String unit,
                              BigDecimal price,
                              String imageUrl,            // full URL, load directly
                              boolean feedbackEnabled,    // false = we never ask about it (e.g. milk)
                              Integer feedbackDelayDays,  // X: days after delivery we ask
                              List<IssueOptionDto> issueOptions) {
    }
}
