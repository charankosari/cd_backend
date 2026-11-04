package com.countrydelight.feedback.web.dto;

import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Summary(LocalDate from,
                          LocalDate to,
                          String productFilter,
                          String areaFilter,
                          Totals totals,
                          List<ProductStat> byProduct,
                          List<IssueStat> byIssue,
                          List<AreaStat> byArea,
                          List<MatrixCell> productAreaMatrix,
                          List<TrendPoint> trend,
                          List<Alert> alerts) {
    }

    public record Totals(long feedbackCount,
                         double avgRating,
                         long lowRatingCount,
                         double lowRatingPct,
                         long withIssueDetails,
                         long withPhoto,
                         long buyAgainAddsToCart) {
    }

    public record ProductStat(String productId,
                              String productName,
                              long count,
                              double avgRating,
                              long lowCount,
                              double lowPct,
                              Map<Integer, Long> ratingDistribution,
                              List<IssueCount> topIssues) {
    }

    public record IssueCount(String issueCode, String label, long count) {
    }

    public record IssueStat(String productId,
                            String productName,
                            String issueCode,
                            String label,
                            long count,
                            double pctOfProductLowRatings) {
    }

    public record AreaStat(String area,
                           String city,
                           long count,
                           double avgRating,
                           long lowCount,
                           double lowPct,
                           String topIssue) {
    }

    public record MatrixCell(String productId, String area, long count, long lowCount, double lowPct) {
    }

    public record TrendPoint(LocalDate date, long count, long lowCount, double avgRating) {
    }

    /** A pattern the quality team should look at. type: HOTSPOT, ISSUE_SPIKE, PRODUCT_QUALITY. */
    public record Alert(String type,
                        String severity,
                        String productId,
                        String area,
                        String issueCode,
                        String title,
                        String message) {
    }

    public record FeedbackRow(Long feedbackId,
                              LocalDate createdOn,
                              int rating,
                              String productId,
                              String productName,
                              List<IssueOptionDto> issues,
                              String note,
                              String photoUrl,
                              Long orderId,
                              Long orderItemId,
                              LocalDate deliveryDate,
                              String area,
                              String city,
                              Long customerId) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record Filters(List<ProductOption> products, List<AreaOption> areas) {
    }

    public record ProductOption(String productId, String name, Integer feedbackDelayDays, List<IssueOptionDto> issueOptions) {
    }

    public record AreaOption(String area, String city) {
    }
}
