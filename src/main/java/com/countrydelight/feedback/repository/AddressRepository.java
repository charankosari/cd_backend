package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Default address first, then oldest first. */
    List<Address> findByCustomerIdOrderByDefaultAddressDescIdAsc(Long customerId);

    Optional<Address> findFirstByCustomerIdAndDefaultAddressTrue(Long customerId);

    long countByCustomerId(Long customerId);
}
