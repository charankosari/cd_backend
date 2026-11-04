package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "customer_seq", initialValue = 100001, allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String phone;

    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String city;

    @Column(length = 500)
    private String demoScenario;

    private Instant createdAt;

    protected Customer() {
    }

    public Customer(String name, String phone, String area, String city, Instant createdAt) {
        this.name = name;
        this.phone = phone;
        this.area = area;
        this.city = city;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getArea() { return area; }
    public String getCity() { return city; }
    public String getDemoScenario() { return demoScenario; }
    public Instant getCreatedAt() { return createdAt; }
}
