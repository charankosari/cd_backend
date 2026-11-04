package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.service.PromptService;
import com.countrydelight.feedback.web.dto.PromptDtos.PromptResponse;
import com.countrydelight.feedback.web.dto.PromptDtos.RateNowRequest;
import com.countrydelight.feedback.web.dto.PromptDtos.RatingRequest;
import com.countrydelight.feedback.web.dto.PromptDtos.RatingResponse;
import com.countrydelight.feedback.web.dto.PromptDtos.SkipResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback-prompts")
public class FeedbackPromptController {

    private final PromptService prompts;

    public FeedbackPromptController(PromptService prompts) {
        this.prompts = prompts;
    }

    @GetMapping("/next")
    public PromptResponse next(@CurrentCustomer Long customerId) {
        return prompts.nextPrompt(customerId);
    }

    /** "Rate now": skip the X-day wait for one delivered item. Returns the card; then use /rating or /skip. */
    @PostMapping("/rate-now")
    public PromptResponse rateNow(@CurrentCustomer Long customerId, @Valid @RequestBody RateNowRequest body) {
        return prompts.rateNow(customerId, body.orderItemId());
    }

    @PostMapping("/{promptId}/rating")
    public RatingResponse rate(@CurrentCustomer Long customerId,
                               @PathVariable Long promptId,
                               @Valid @RequestBody RatingRequest body) {
        return prompts.submitRating(customerId, promptId, body.rating());
    }

    @PostMapping("/{promptId}/skip")
    public SkipResponse skip(@CurrentCustomer Long customerId, @PathVariable Long promptId) {
        return prompts.skip(customerId, promptId);
    }
}
