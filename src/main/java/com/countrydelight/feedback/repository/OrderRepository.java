package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
}
