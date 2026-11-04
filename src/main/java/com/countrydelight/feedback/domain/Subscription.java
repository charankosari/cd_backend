package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
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

    private int quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionFrequency frequency = SubscriptionFrequency.DAILY;

    private Long addressId;

    @Column(length = 300)
    private String deliveryAddress;

    private LocalDate lastGeneratedOn;

    private Instant createdAt;

    protected Subscription() {
    }

    public Subscription(Long customerId, String productId, SubscriptionStatus status, LocalDate startDate, LocalDate endDate) {
        this.customerId = customerId;
        this.productId = productId;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static Subscription create(Long customerId, String productId, int quantity, SubscriptionFrequency frequency,
                                      LocalDate startDate, Instant createdAt) {
        Subscription s = new Subscription(customerId, productId, SubscriptionStatus.ACTIVE, startDate, null);
        s.quantity = quantity;
        s.frequency = frequency;
        s.lastGeneratedOn = startDate.minusDays(1);
        s.createdAt = createdAt;
        return s;
    }

    public boolean coversDate(LocalDate date) {
        return status != SubscriptionStatus.CANCELLED
                && !startDate.isAfter(date)
                && (endDate == null || !endDate.isBefore(date));
    }

    public boolean deliversOn(LocalDate date) {
        return status == SubscriptionStatus.ACTIVE && frequency.deliversOn(startDate, date);
    }

    public LocalDate generateFrom(LocalDate today) {
        if (lastGeneratedOn != null) {
            return lastGeneratedOn.plusDays(1);
        }
        return startDate.isAfter(today) ? startDate : today;
    }

    public LocalDate nextDelivery(LocalDate today) {
        if (status != SubscriptionStatus.ACTIVE) {
            return null;
        }
        LocalDate d = startDate.isAfter(today) ? startDate : today.plusDays(1);
        while (!frequency.deliversOn(startDate, d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    public void deliverTo(Address address) {
        this.addressId = address == null ? null : address.getId();
        this.deliveryAddress = address == null ? null : address.fullText();
    }

    public void setPlan(int quantity, SubscriptionFrequency frequency) {
        this.quantity = quantity;
        this.frequency = frequency;
    }

    public void markGeneratedUpTo(LocalDate date) {
        if (lastGeneratedOn == null || date.isAfter(lastGeneratedOn)) {
            this.lastGeneratedOn = date;
        }
    }

    public void pause() {
        this.status = SubscriptionStatus.PAUSED;
    }

    public void resume(LocalDate today) {
        this.status = SubscriptionStatus.ACTIVE;
        markGeneratedUpTo(today);
    }

    public void cancel(LocalDate today) {
        this.status = SubscriptionStatus.CANCELLED;
        this.endDate = today;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public SubscriptionStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getQuantity() { return quantity; }
    public SubscriptionFrequency getFrequency() { return frequency; }
    public Long getAddressId() { return addressId; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public LocalDate getLastGeneratedOn() { return lastGeneratedOn; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(SubscriptionStatus status) { this.status = status; }
}
