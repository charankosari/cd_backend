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
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String productId;

    private int quantity;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    private CartItemSource source;

    private Long feedbackId;

    private Instant createdAt;

    protected CartItem() {
    }

    public CartItem(Long customerId, String productId, int quantity, LocalDate deliveryDate,
                    CartItemSource source, Long feedbackId, Instant createdAt) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.deliveryDate = deliveryDate;
        this.source = source;
        this.feedbackId = feedbackId;
        this.createdAt = createdAt;
    }

    public void addQuantity(int more) {
        this.quantity += more;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public CartItemSource getSource() { return source; }
    public Long getFeedbackId() { return feedbackId; }
    public Instant getCreatedAt() { return createdAt; }
}
