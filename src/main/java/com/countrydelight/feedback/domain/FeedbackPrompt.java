package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * One "ask" shown to a user about one delivered product. Created the first time the card is shown.
 * Tracks impressions so we can detect "ignored" and enforce the frequency cap.
 */
@Entity
@Table(name = "feedback_prompts",
        uniqueConstraints = @UniqueConstraint(name = "uk_prompt_order_item", columnNames = "order_item_id"))
public class FeedbackPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String productId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    @Column(nullable = false)
    private LocalDate dueOn;

    @Column(nullable = false)
    private LocalDate expiresOn;

    private String area;
    private String city;

    /** Comma separated PromptReason names. */
    private String reasons;

    private int priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromptStatus status = PromptStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromptSource source = PromptSource.AUTO;

    private Instant createdAt;
    private LocalDate firstShownOn;
    private LocalDate lastShownOn;
    private int shownDays;
    private LocalDate resolvedOn;
    private Instant resolvedAt;

    protected FeedbackPrompt() {
    }

    public FeedbackPrompt(Long customerId, String productId, Long orderItemId, Long orderId, LocalDate deliveryDate,
                          LocalDate dueOn, LocalDate expiresOn, String area, String city,
                          List<PromptReason> reasons, int priorityScore, Instant createdAt) {
        this.customerId = customerId;
        this.productId = productId;
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.deliveryDate = deliveryDate;
        this.dueOn = dueOn;
        this.expiresOn = expiresOn;
        this.area = area;
        this.city = city;
        this.reasons = String.join(",", reasons.stream().map(Enum::name).toList());
        this.priorityScore = priorityScore;
        this.createdAt = createdAt;
    }

    /** Counts an impression at most once per calendar day. */
    public void recordImpression(LocalDate today) {
        if (firstShownOn == null) {
            firstShownOn = today;
        }
        if (!today.equals(lastShownOn)) {
            shownDays++;
            lastShownOn = today;
        }
    }

    /** The user chose "Rate now": this prompt is theirs, and a skipped/ignored one opens again. */
    public void openForUser() {
        this.source = PromptSource.USER;
        this.status = PromptStatus.PENDING;
        this.resolvedOn = null;
        this.resolvedAt = null;
    }

    public boolean isUserInitiated() {
        return source == PromptSource.USER;
    }

    public void resolve(PromptStatus newStatus, LocalDate on, Instant at) {
        this.status = newStatus;
        this.resolvedOn = on;
        this.resolvedAt = at;
    }

    public List<PromptReason> reasonList() {
        if (reasons == null || reasons.isBlank()) {
            return List.of();
        }
        return Arrays.stream(reasons.split(",")).map(PromptReason::valueOf).toList();
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public Long getOrderItemId() { return orderItemId; }
    public Long getOrderId() { return orderId; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public LocalDate getDueOn() { return dueOn; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public String getArea() { return area; }
    public String getCity() { return city; }
    public int getPriorityScore() { return priorityScore; }
    public PromptStatus getStatus() { return status; }
    public PromptSource getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
    public LocalDate getFirstShownOn() { return firstShownOn; }
    public LocalDate getLastShownOn() { return lastShownOn; }
    public int getShownDays() { return shownDays; }
    public LocalDate getResolvedOn() { return resolvedOn; }
    public Instant getResolvedAt() { return resolvedAt; }
}
