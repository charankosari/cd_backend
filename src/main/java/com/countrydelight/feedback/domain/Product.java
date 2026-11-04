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

    private Integer feedbackDelayDays;

    private String imageUrl;

    @Column(length = 300)
    private String description;

    private String category;

    private boolean subscribable = true;

    protected Product() {
    }

    public Product(String id, String name, String unit, BigDecimal price, Integer feedbackDelayDays, String imageUrl) {
        this(id, name, unit, price, feedbackDelayDays, imageUrl, null, null);
    }

    public Product(String id, String name, String unit, BigDecimal price, Integer feedbackDelayDays, String imageUrl,
                   String description, String category) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.price = price;
        this.feedbackDelayDays = feedbackDelayDays;
        this.imageUrl = imageUrl;
        this.description = description;
        this.category = category;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getPrice() { return price; }
    public Integer getFeedbackDelayDays() { return feedbackDelayDays; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }

    public boolean isSubscribable() { return subscribable; }

    public void setFeedbackDelayDays(Integer feedbackDelayDays) { this.feedbackDelayDays = feedbackDelayDays; }
    public void setSubscribable(boolean subscribable) { this.subscribable = subscribable; }
}
