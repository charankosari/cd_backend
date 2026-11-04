package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByDemoScenarioIsNotNullOrderByIdAsc();

    Optional<Customer> findByPhone(String phone);

    boolean existsByPhone(String phone);

    @Transactional
    @Modifying
    @Query(value = "insert into customers (id, name, phone, area, city, demo_scenario, created_at) "
            + "values (:id, :name, :phone, :area, :city, :scenario, :createdAt)", nativeQuery = true)
    void insertWithId(@Param("id") Long id, @Param("name") String name, @Param("phone") String phone,
                      @Param("area") String area, @Param("city") String city,
                      @Param("scenario") String scenario, @Param("createdAt") Instant createdAt);
}
