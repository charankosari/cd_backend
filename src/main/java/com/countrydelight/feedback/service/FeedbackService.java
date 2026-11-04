package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.repository.FeedbackRepository;
import com.countrydelight.feedback.web.dto.FeedbackDtos.FeedbackDetailResponse;
import com.countrydelight.feedback.web.dto.FeedbackDtos.IssueDetailsRequest;
import com.countrydelight.feedback.web.dto.FeedbackDtos.PhotoUploadResponse;
import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Issue capture for low ratings (chips + optional note + optional photo). */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbacks;
    private final CatalogService catalog;
    private final PhotoStorageService photos;
    private final TimeProvider time;
    private final FeedbackProperties props;

    public FeedbackService(FeedbackRepository feedbacks, CatalogService catalog, PhotoStorageService photos,
                           TimeProvider time, FeedbackProperties props) {
        this.feedbacks = feedbacks;
        this.catalog = catalog;
        this.photos = photos;
        this.time = time;
        this.props = props;
    }

    @Transactional
    public FeedbackDetailResponse submitIssueDetails(Long customerId, Long feedbackId, IssueDetailsRequest req) {
        Feedback f = owned(customerId, feedbackId);
        requireLowRating(f);

        Set<String> allowed = catalog.issueOptions(f.getProductId()).stream()
                .map(IssueOptionDto::code).collect(Collectors.toSet());
        Set<String> codes = new LinkedHashSet<>();
        for (String raw : req.issueCodes()) {
            if (raw != null && !raw.isBlank()) {
                codes.add(raw.trim().toUpperCase());
            }
        }
        if (codes.isEmpty()) {
            throw ApiException.badRequest("ISSUE_REQUIRED", "Pick at least one issue");
        }
        List<String> invalid = codes.stream().filter(c -> !allowed.contains(c)).toList();
        if (!invalid.isEmpty()) {
            throw ApiException.badRequest("INVALID_ISSUE_CODE",
                    "Not valid for " + f.getProductId() + ": " + invalid + ". Allowed: " + allowed);
        }
        String note = req.note() == null ? null : req.note().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        if (note != null && note.length() > props.noteMaxLength()) {
            throw ApiException.badRequest("NOTE_TOO_LONG", "Note must be at most " + props.noteMaxLength() + " characters");
        }
        String photoUrl = req.photoUrl() == null || req.photoUrl().isBlank() ? null : req.photoUrl().trim();

        f.setIssueDetails(codes, note, photoUrl, time.now());
        return toDetail(f, "Thanks for telling us. Our quality team will look into it.");
    }

    @Transactional
    public PhotoUploadResponse uploadPhoto(Long customerId, Long feedbackId, MultipartFile file) {
        Feedback f = owned(customerId, feedbackId);
        requireLowRating(f);
        String url = photos.store(feedbackId, file);
        f.setPhotoUrl(url);
        return new PhotoUploadResponse(feedbackId, url);
    }

    @Transactional(readOnly = true)
    public FeedbackDetailResponse get(Long customerId, Long feedbackId) {
        return toDetail(owned(customerId, feedbackId), null);
    }

    private void requireLowRating(Feedback f) {
        if (f.getRating() >= props.highRatingThreshold()) {
            throw ApiException.badRequest("DETAILS_NOT_NEEDED",
                    "Issue details are only collected for ratings below " + props.highRatingThreshold());
        }
    }

    private Feedback owned(Long customerId, Long feedbackId) {
        return feedbacks.findById(feedbackId)
                .filter(f -> f.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Feedback " + feedbackId));
    }

    private FeedbackDetailResponse toDetail(Feedback f, String message) {
        return new FeedbackDetailResponse(f.getId(), f.getPromptId(), f.getCustomerId(), f.getProductId(),
                f.getProductName(), f.getRating(),
                catalog.toDtos(f.getProductId(), f.getIssueCodes(), catalog.issueLabelIndex()),
                f.getNote(), f.getPhotoUrl(), f.getOrderId(), f.getOrderItemId(), f.getDeliveryDate(),
                f.getArea(), f.getCity(), f.getCreatedAt(), message);
    }
}
