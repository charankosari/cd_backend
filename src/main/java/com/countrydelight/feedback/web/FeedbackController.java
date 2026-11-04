package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.service.FeedbackService;
import com.countrydelight.feedback.web.dto.FeedbackDtos.FeedbackDetailResponse;
import com.countrydelight.feedback.web.dto.FeedbackDtos.IssueDetailsRequest;
import com.countrydelight.feedback.web.dto.FeedbackDtos.PhotoUploadResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedback;

    public FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    @PutMapping("/{feedbackId}/issues")
    public FeedbackDetailResponse issues(@CurrentCustomer Long customerId,
                                         @PathVariable Long feedbackId,
                                         @Valid @RequestBody IssueDetailsRequest body) {
        return feedback.submitIssueDetails(customerId, feedbackId, body);
    }

    @PostMapping(value = "/{feedbackId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoUploadResponse photo(@CurrentCustomer Long customerId,
                                     @PathVariable Long feedbackId,
                                     @RequestPart("file") MultipartFile file) {
        return feedback.uploadPhoto(customerId, feedbackId, file);
    }

    @GetMapping("/{feedbackId}")
    public FeedbackDetailResponse get(@CurrentCustomer Long customerId, @PathVariable Long feedbackId) {
        return feedback.get(customerId, feedbackId);
    }
}
