package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
}
