package com.countrydelight.feedback.web;

import com.countrydelight.feedback.service.DashboardService;
import com.countrydelight.feedback.web.dto.DashboardDtos.FeedbackRow;
import com.countrydelight.feedback.web.dto.DashboardDtos.Filters;
import com.countrydelight.feedback.web.dto.DashboardDtos.FollowUpRequest;
import com.countrydelight.feedback.web.dto.DashboardDtos.Page;
import com.countrydelight.feedback.web.dto.DashboardDtos.Summary;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) String productId,
                           @RequestParam(required = false) String area) {
        return dashboard.summary(from, to, productId, area);
    }

    @GetMapping("/feedback")
    public Page<FeedbackRow> feedback(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String productId,
                                      @RequestParam(required = false) String area,
                                      @RequestParam(required = false) String issueCode,
                                      @RequestParam(required = false) Integer maxRating,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return dashboard.list(from, to, productId, area, issueCode, maxRating, page, size);
    }

    @GetMapping("/filters")
    public Filters filters() {
        return dashboard.filters();
    }

    /** Quality team: set status (NEW / IN_PROGRESS / RESOLVED) and an internal note. Shared by everyone on the team. */
    @PutMapping("/feedback/{feedbackId}/follow-up")
    public FeedbackRow followUp(@PathVariable Long feedbackId, @Valid @RequestBody FollowUpRequest body) {
        return dashboard.updateFollowUp(feedbackId, body.status(), body.internalNote());
    }
}
