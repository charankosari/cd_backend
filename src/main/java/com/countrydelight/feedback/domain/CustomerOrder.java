package com.countrydelight.feedback.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String city;

    private Long addressId;

    @Column(length = 300)
    private String deliveryAddress;

    private Instant placedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Long customerId, LocalDate deliveryDate, OrderStatus status, String area, String city) {
        this.customerId = customerId;
        this.deliveryDate = deliveryDate;
        this.status = status;
        this.area = area;
        this.city = city;
        this.placedAt = deliveryDate.minusDays(1).atTime(20, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
    }

    public OrderItem addItem(String productId, int quantity, BigDecimal unitPrice, PurchaseType purchaseType) {
        OrderItem item = new OrderItem(this, productId, quantity, unitPrice, purchaseType);
        items.add(item);
        return item;
    }

    public void deliverTo(Address address) {
        this.addressId = address.getId();
        this.area = address.getArea();
        this.city = address.getCity();
        this.deliveryAddress = address.fullText();
    }

    public Long getAddressId() { return addressId; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }

    public void markDelivered() {
        this.status = OrderStatus.DELIVERED;
    }

    public void markDeliveredOn(LocalDate date) {
        this.status = OrderStatus.DELIVERED;
        this.deliveryDate = date;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public OrderStatus getStatus() { return status; }
    public String getArea() { return area; }
    public String getCity() { return city; }
    public List<OrderItem> getItems() { return items; }
}
