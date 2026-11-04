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
import java.time.LocalDate;
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

    /** Snapshot of the delivery area at order time, so quality can trace it even if the user moves. */
    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String city;

    /** The address it was delivered to (snapshot of the text, so later edits don't change history). */
    private Long addressId;

    @Column(length = 300)
    private String deliveryAddress;

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
    }

    public OrderItem addItem(String productId, int quantity, BigDecimal unitPrice, PurchaseType purchaseType) {
        OrderItem item = new OrderItem(this, productId, quantity, unitPrice, purchaseType);
        items.add(item);
        return item;
    }

    /** Deliver to this address: area/city (used by the quality dashboard) and the full address text. */
    public void deliverTo(Address address) {
        this.addressId = address.getId();
        this.area = address.getArea();
        this.city = address.getCity();
        this.deliveryAddress = address.fullText();
    }

    public Long getAddressId() { return addressId; }
    public String getDeliveryAddress() { return deliveryAddress; }

    public void markDelivered() {
        this.status = OrderStatus.DELIVERED;
    }

    /** Delivered on a specific day (e.g. "Mark delivered" on the dashboard before the planned date). */
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
