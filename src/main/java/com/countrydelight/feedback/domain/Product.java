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

    /**
     * Image path served by this backend (e.g. "/products/paneer.jpg", files in resources/static/products),
     * or a full https URL. API responses always turn it into a full URL (CatalogService.imageUrl).
     */
    private String imageUrl;

    @Column(length = 300)
    private String description;

    /** Dairy, Bakery, Staples… for grouping in the app. */
    private String category;

    /** Can be bought as a daily / alternate-day subscription (milk yes, atta no). */
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
