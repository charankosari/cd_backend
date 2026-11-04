package com.countrydelight.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "issue_options", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "code"}))
public class IssueOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private String label;

    private int sortOrder;

    protected IssueOption() {
    }

    public IssueOption(String productId, String code, String label, int sortOrder) {
        this.productId = productId;
        this.code = code;
        this.label = label;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getProductId() { return productId; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public int getSortOrder() { return sortOrder; }
}
