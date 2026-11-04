package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.service.AddressService;
import com.countrydelight.feedback.web.dto.AddressDtos.AddressList;
import com.countrydelight.feedback.web.dto.AddressDtos.AddressRequest;
import com.countrydelight.feedback.web.dto.AddressDtos.AddressView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in household's delivery addresses. */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public AddressList list(@CurrentCustomer Long customerId) {
        return addresses.list(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressView add(@CurrentCustomer Long customerId, @Valid @RequestBody AddressRequest body) {
        return addresses.add(customerId, body);
    }

    @PutMapping("/{addressId}")
    public AddressView update(@CurrentCustomer Long customerId, @PathVariable Long addressId,
                              @Valid @RequestBody AddressRequest body) {
        return addresses.update(customerId, addressId, body);
    }

    @PostMapping("/{addressId}/default")
    public AddressList makeDefault(@CurrentCustomer Long customerId, @PathVariable Long addressId) {
        return addresses.makeDefault(customerId, addressId);
    }

    @DeleteMapping("/{addressId}")
    public AddressList delete(@CurrentCustomer Long customerId, @PathVariable Long addressId) {
        return addresses.delete(customerId, addressId);
    }
}
