package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.domain.Address;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.repository.AddressRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final AddressRepository addresses;

    public CustomerService(CustomerRepository customers, AddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    @Transactional
    public Customer create(String phone, String name, String area, String city) {
        if (customers.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This number already has an account. Log in instead");
        }
        Customer created;
        try {
            created = customers.saveAndFlush(new Customer(name.trim(), phone, area.trim(), city.trim(), Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This number already has an account. Log in instead");
        }
        addresses.save(new Address(created.getId(), "Home", null, null, null, area.trim(), city.trim(), null, true,
                Instant.now()));
        return created;
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByPhone(String phone) {
        return customers.findByPhone(phone);
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer " + id));
    }
}
