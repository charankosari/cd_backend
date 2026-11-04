package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A delivery address of a household. Each household has one default address. */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    /** "Home", "Work", "Parents' home"… */
    @Column(nullable = false, length = 30)
    private String label;

    /** Flat / house number and building, e.g. "Flat 402, Tower B, Sunshine Residency". */
    @Column(length = 120)
    private String line1;

    /** Street / road. */
    @Column(length = 120)
    private String line2;

    @Column(length = 80)
    private String landmark;

    @Column(nullable = false, length = 80)
    private String area;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(length = 6)
    private String pincode;

    private boolean defaultAddress;

    private Instant createdAt;

    protected Address() {
    }

    public Address(Long customerId, String label, String line1, String line2, String landmark, String area,
                   String city, String pincode, boolean defaultAddress, Instant createdAt) {
        this.customerId = customerId;
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
        this.landmark = landmark;
        this.area = area;
        this.city = city;
        this.pincode = pincode;
        this.defaultAddress = defaultAddress;
        this.createdAt = createdAt;
    }

    public void update(String label, String line1, String line2, String landmark, String area, String city,
                       String pincode) {
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
        this.landmark = landmark;
        this.area = area;
        this.city = city;
        this.pincode = pincode;
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    /** One line for orders / dashboard, e.g. "Flat 402, Tower B, Sunshine Residency, Golf Course Rd, Sector 45, Gurugram 122003". */
    public String fullText() {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{line1, line2, landmark == null ? null : "near " + landmark, area}) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(part.trim());
            }
        }
        sb.append(sb.isEmpty() ? "" : ", ").append(city);
        if (pincode != null && !pincode.isBlank()) {
            sb.append(' ').append(pincode);
        }
        return sb.toString();
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getLabel() { return label; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getLandmark() { return landmark; }
    public String getArea() { return area; }
    public String getCity() { return city; }
    public String getPincode() { return pincode; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
