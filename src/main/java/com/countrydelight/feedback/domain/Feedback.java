package com.countrydelight.feedback.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A rating plus optional issue details. Order details (product, order, delivery date, area) are copied
 * onto the row so the quality team can trace it even if orders or customers change later.
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long promptId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String productId;

    private String productName;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    private String area;
    private String city;

    @Enumerated(EnumType.STRING)
    private PurchaseType purchaseType;

    private int rating;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "feedback_issues", joinColumns = @JoinColumn(name = "feedback_id"))
    @Column(name = "issue_code")
    private Set<String> issueCodes = new LinkedHashSet<>();

    @Column(length = 500)
    private String note;

    private String photoUrl;

    private Instant createdAt;
    private LocalDate createdOn;
    private Instant detailsSubmittedAt;

    /** Quality-team follow-up, set from the dashboard. Internal only. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FollowUpStatus followUpStatus = FollowUpStatus.NEW;

    @Column(length = 1000)
    private String internalNote;

    private Instant followUpUpdatedAt;

    protected Feedback() {
    }

    public void updateFollowUp(FollowUpStatus status, String internalNote, Instant at) {
        this.followUpStatus = status;
        this.internalNote = internalNote;
        this.followUpUpdatedAt = at;
    }

    public Feedback(FeedbackPrompt prompt, String productName, PurchaseType purchaseType, int rating,
                    Instant createdAt, LocalDate createdOn) {
        this.promptId = prompt.getId();
        this.customerId = prompt.getCustomerId();
        this.productId = prompt.getProductId();
        this.productName = productName;
        this.orderId = prompt.getOrderId();
        this.orderItemId = prompt.getOrderItemId();
        this.deliveryDate = prompt.getDeliveryDate();
        this.area = prompt.getArea();
        this.city = prompt.getCity();
        this.purchaseType = purchaseType;
        this.rating = rating;
        this.createdAt = createdAt;
        this.createdOn = createdOn;
    }

    public void setIssueDetails(Collection<String> codes, String note, String photoUrl, Instant at) {
        this.issueCodes.clear();
        this.issueCodes.addAll(codes);
        this.note = note;
        if (photoUrl != null) {
            this.photoUrl = photoUrl;
        }
        this.detailsSubmittedAt = at;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Long getId() { return id; }
    public Long getPromptId() { return promptId; }
    public Long getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Long getOrderId() { return orderId; }
    public Long getOrderItemId() { return orderItemId; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public String getArea() { return area; }
    public String getCity() { return city; }
    public PurchaseType getPurchaseType() { return purchaseType; }
    public int getRating() { return rating; }
    public Set<String> getIssueCodes() { return issueCodes; }
    public String getNote() { return note; }
    public String getPhotoUrl() { return photoUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public LocalDate getCreatedOn() { return createdOn; }
    public Instant getDetailsSubmittedAt() { return detailsSubmittedAt; }
    public FollowUpStatus getFollowUpStatus() { return followUpStatus; }
    public String getInternalNote() { return internalNote; }
    public Instant getFollowUpUpdatedAt() { return followUpUpdatedAt; }
}
