package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Creating and reading households (users). */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /** Creates a new household. The phone must already be normalised and OTP-verified by the caller. */
    @Transactional
    public Customer create(String phone, String name, String area, String city) {
        if (customers.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This number already has an account. Log in instead");
        }
        try {
            // saveAndFlush = INSERT now, so a duplicate phone (two signups at the same moment)
            // fails here, inside the try, instead of later at commit.
            return customers.saveAndFlush(new Customer(name.trim(), phone, area.trim(), city.trim(), Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This number already has an account. Log in instead");
        }
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
