package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.Address;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import com.countrydelight.feedback.repository.AddressRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.OrderRepository;
import com.countrydelight.feedback.repository.ProductRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SubscriptionDeliveryService {

    private final SubscriptionRepository subscriptions;
    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final AddressRepository addresses;
    private final ProductRepository products;
    private final TimeProvider time;

    public SubscriptionDeliveryService(SubscriptionRepository subscriptions, OrderRepository orders,
                                       CustomerRepository customers, AddressRepository addresses,
                                       ProductRepository products, TimeProvider time) {
        this.subscriptions = subscriptions;
        this.orders = orders;
        this.customers = customers;
        this.addresses = addresses;
        this.products = products;
        this.time = time;
    }

    @Transactional
    public int generateFor(Long customerId) {
        return generate(subscriptions.findByCustomerIdAndStatus(customerId, SubscriptionStatus.ACTIVE));
    }

    @Transactional
    public int generateAll() {
        return generate(subscriptions.findByStatus(SubscriptionStatus.ACTIVE));
    }

    private int generate(List<Subscription> active) {
        LocalDate today = time.today();
        int created = 0;
        Map<Long, List<Subscription>> byCustomer = active.stream()
                .filter(s -> !s.generateFrom(today).isAfter(today))
                .collect(Collectors.groupingBy(Subscription::getCustomerId));
        for (Map.Entry<Long, List<Subscription>> e : byCustomer.entrySet()) {
            Customer customer = customers.findById(e.getKey()).orElse(null);
            if (customer == null) {
                continue;
            }
            List<Subscription> subs = e.getValue();
            LocalDate from = subs.stream().map(s -> s.generateFrom(today)).min(Comparator.naturalOrder()).orElse(today);
            for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
                final LocalDate d = day;
                List<Subscription> due = subs.stream()
                        .filter(s -> !s.generateFrom(today).isAfter(d) && s.deliversOn(d))
                        .toList();
                Map<Long, List<Subscription>> byAddress = due.stream()
                        .collect(Collectors.groupingBy(s -> Objects.requireNonNullElse(s.getAddressId(), -1L)));
                for (List<Subscription> group : byAddress.values()) {
                    CustomerOrder order = new CustomerOrder(customer.getId(), d, OrderStatus.DELIVERED,
                            customer.getArea(), customer.getCity());
                    Address address = addressFor(customer.getId(), group.get(0).getAddressId());
                    if (address != null) {
                        order.deliverTo(address);
                    }
                    List<Subscription> added = new ArrayList<>();
                    for (Subscription s : group) {
                        Product p = products.findById(s.getProductId()).orElse(null);
                        if (p != null) {
                            order.addItem(p.getId(), s.getQuantity(), p.getPrice(), PurchaseType.SUBSCRIPTION);
                            added.add(s);
                        }
                    }
                    if (!added.isEmpty()) {
                        orders.save(order);
                        created++;
                    }
                }
            }
            subs.forEach(s -> s.markGeneratedUpTo(today));
        }
        return created;
    }

    private Address addressFor(Long customerId, Long addressId) {
        if (addressId != null) {
            Address a = addresses.findById(addressId).orElse(null);
            if (a != null && a.getCustomerId().equals(customerId)) {
                return a;
            }
        }
        return addresses.findFirstByCustomerIdAndDefaultAddressTrue(customerId).orElse(null);
    }
}
