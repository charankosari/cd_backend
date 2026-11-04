package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.domain.Address;
import com.countrydelight.feedback.repository.AddressRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.web.dto.AddressDtos.AddressList;
import com.countrydelight.feedback.web.dto.AddressDtos.AddressRequest;
import com.countrydelight.feedback.web.dto.AddressDtos.AddressView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AddressService {

    private static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final CustomerRepository customers;

    public AddressService(AddressRepository addresses, CustomerRepository customers) {
        this.addresses = addresses;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public AddressList list(Long customerId) {
        requireCustomer(customerId);
        List<AddressView> views = addresses.findByCustomerIdOrderByDefaultAddressDescIdDesc(customerId).stream()
                .map(AddressService::view).toList();
        Long defaultId = views.stream().filter(AddressView::isDefault).map(AddressView::addressId).findFirst().orElse(null);
        return new AddressList(views, defaultId);
    }

    @Transactional
    public AddressView add(Long customerId, AddressRequest req) {
        requireCustomer(customerId);
        long count = addresses.countByCustomerId(customerId);
        if (count >= MAX_ADDRESSES) {
            throw ApiException.badRequest("ADDRESS_LIMIT", "You can save up to " + MAX_ADDRESSES + " addresses");
        }
        boolean makeDefault = count == 0 || Boolean.TRUE.equals(req.makeDefault());
        if (makeDefault) {
            clearDefault(customerId);
        }
        Address a = addresses.save(new Address(customerId, req.label().trim(), req.line1().trim(), trim(req.line2()),
                trim(req.landmark()), req.area().trim(), req.city().trim(), req.pincode(), makeDefault, Instant.now()));
        return view(a);
    }

    @Transactional
    public AddressView update(Long customerId, Long addressId, AddressRequest req) {
        Address a = owned(customerId, addressId);
        a.update(req.label().trim(), req.line1().trim(), trim(req.line2()), trim(req.landmark()),
                req.area().trim(), req.city().trim(), req.pincode());
        if (Boolean.TRUE.equals(req.makeDefault()) && !a.isDefaultAddress()) {
            clearDefault(customerId);
            a.setDefaultAddress(true);
        }
        return view(a);
    }

    @Transactional
    public AddressList makeDefault(Long customerId, Long addressId) {
        Address a = owned(customerId, addressId);
        clearDefault(customerId);
        a.setDefaultAddress(true);
        addresses.flush();
        return list(customerId);
    }

    @Transactional
    public AddressList delete(Long customerId, Long addressId) {
        Address a = owned(customerId, addressId);
        if (addresses.countByCustomerId(customerId) <= 1) {
            throw ApiException.conflict("LAST_ADDRESS", "Keep at least one delivery address");
        }
        boolean wasDefault = a.isDefaultAddress();
        addresses.delete(a);
        addresses.flush();
        if (wasDefault) {
            addresses.findByCustomerIdOrderByDefaultAddressDescIdAsc(customerId).stream().findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
            addresses.flush();
        }
        return list(customerId);
    }

    @Transactional(readOnly = true)
    public Optional<Address> forDelivery(Long customerId, Long addressId) {
        if (addressId != null) {
            return Optional.of(owned(customerId, addressId));
        }
        return addresses.findFirstByCustomerIdAndDefaultAddressTrue(customerId);
    }

    private void clearDefault(Long customerId) {
        addresses.findFirstByCustomerIdAndDefaultAddressTrue(customerId).ifPresent(d -> d.setDefaultAddress(false));
        addresses.flush();
    }

    private Address owned(Long customerId, Long addressId) {
        return addresses.findById(addressId)
                .filter(a -> a.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Address " + addressId));
    }

    private void requireCustomer(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw ApiException.notFound("Customer " + customerId);
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static AddressView view(Address a) {
        return new AddressView(a.getId(), a.getLabel(), a.getLine1(), a.getLine2(), a.getLandmark(), a.getArea(),
                a.getCity(), a.getPincode(), a.isDefaultAddress(), a.fullText());
    }
}
