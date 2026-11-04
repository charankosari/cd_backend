package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private String name;

    private String unit;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** X: days after delivery to ask for feedback. Null means we never ask for this product. */
    private Integer feedbackDelayDays;

    private String imageUrl;

    protected Product() {
    }

    public Product(String id, String name, String unit, BigDecimal price, Integer feedbackDelayDays, String imageUrl) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.price = price;
        this.feedbackDelayDays = feedbackDelayDays;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getPrice() { return price; }
    public Integer getFeedbackDelayDays() { return feedbackDelayDays; }
    public String getImageUrl() { return imageUrl; }

    public void setFeedbackDelayDays(Integer feedbackDelayDays) { this.feedbackDelayDays = feedbackDelayDays; }
}
