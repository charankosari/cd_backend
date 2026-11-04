package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    protected Subscription() {
    }

    public Subscription(Long customerId, String productId, SubscriptionStatus status, LocalDate startDate, LocalDate endDate) {
        this.customerId = customerId;
        this.productId = productId;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** True if the customer counts as "subscribed" to this product on the given date (ACTIVE or PAUSED). */
    public boolean coversDate(LocalDate date) {
        return status != SubscriptionStatus.CANCELLED
                && !startDate.isAfter(date)
                && (endDate == null || !endDate.isBefore(date));
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public SubscriptionStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    public void setStatus(SubscriptionStatus status) { this.status = status; }
}
