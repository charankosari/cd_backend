package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.Address;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionFrequency;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.SubscribeRequest;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.SubscriptionList;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.SubscriptionView;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.UpdateSubscriptionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final CatalogService catalog;
    private final AddressService addresses;
    private final SubscriptionDeliveryService deliveries;
    private final TimeProvider time;
    private final FeedbackProperties props;

    public SubscriptionService(SubscriptionRepository subscriptions, CustomerRepository customers,
                               CatalogService catalog, AddressService addresses,
                               SubscriptionDeliveryService deliveries, TimeProvider time, FeedbackProperties props) {
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.catalog = catalog;
        this.addresses = addresses;
        this.deliveries = deliveries;
        this.time = time;
        this.props = props;
    }

    @Transactional
    public SubscriptionList list(Long customerId) {
        requireCustomer(customerId);
        deliveries.generateFor(customerId);
        List<SubscriptionView> views = subscriptions.findByCustomerId(customerId).stream()
                .sorted(Comparator.comparing(Subscription::getId, Comparator.reverseOrder()))
                .map(s -> view(s, null))
                .toList();
        int active = (int) views.stream().filter(v -> v.status().equals("ACTIVE")).count();
        BigDecimal monthly = views.stream().filter(v -> v.status().equals("ACTIVE"))
                .map(SubscriptionView::estimatedMonthlyCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SubscriptionList(views, active, monthly);
    }

    @Transactional
    public SubscriptionView subscribe(Long customerId, SubscribeRequest req) {
        requireCustomer(customerId);
        Product product = catalog.product(req.productId().trim().toUpperCase());
        if (!product.isSubscribable()) {
            throw ApiException.badRequest("NOT_SUBSCRIBABLE", product.getName() + " can't be subscribed. Order it once instead");
        }
        boolean live = subscriptions.findByCustomerId(customerId).stream()
                .anyMatch(s -> s.getProductId().equals(product.getId()) && s.getStatus() != SubscriptionStatus.CANCELLED);
        if (live) {
            throw ApiException.conflict("ALREADY_SUBSCRIBED", "You already have a " + product.getName() + " subscription");
        }
        LocalDate today = time.today();
        LocalDate start = req.startDate() != null ? req.startDate() : today.plusDays(1);
        if (!start.isAfter(today) || start.isAfter(today.plusDays(props.buyAgainMaxDaysAhead()))) {
            throw ApiException.badRequest("INVALID_START_DATE",
                    "Start date must be between tomorrow and " + today.plusDays(props.buyAgainMaxDaysAhead()));
        }
        Subscription s = Subscription.create(customerId, product.getId(), req.quantity() == null ? 1 : req.quantity(),
                req.frequency() == null ? SubscriptionFrequency.DAILY : req.frequency(), start, time.now());
        s.deliverTo(addresses.forDelivery(customerId, req.addressId()).orElse(null));
        subscriptions.save(s);
        return view(s, product.getName() + " subscription starts " + PromptService.CARD_DATE.format(start));
    }

    @Transactional
    public SubscriptionView update(Long customerId, Long id, UpdateSubscriptionRequest req) {
        if (req.quantity() == null && req.frequency() == null && req.addressId() == null) {
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "Send quantity, frequency and/or addressId");
        }
        Subscription s = live(customerId, id);
        deliveries.generateFor(customerId);
        s.setPlan(req.quantity() != null ? req.quantity() : s.getQuantity(),
                req.frequency() != null ? req.frequency() : s.getFrequency());
        if (req.addressId() != null) {
            Address a = addresses.forDelivery(customerId, req.addressId()).orElseThrow();
            s.deliverTo(a);
        }
        return view(s, "Updated. Changes apply from the next delivery");
    }

    @Transactional
    public SubscriptionView pause(Long customerId, Long id) {
        Subscription s = live(customerId, id);
        deliveries.generateFor(customerId);
        s.pause();
        return view(s, "Paused. No deliveries until you resume");
    }

    @Transactional
    public SubscriptionView resume(Long customerId, Long id) {
        Subscription s = live(customerId, id);
        s.resume(time.today());
        return view(s, "Resumed. Next delivery " + PromptService.CARD_DATE.format(s.nextDelivery(time.today())));
    }

    @Transactional
    public SubscriptionView cancel(Long customerId, Long id) {
        Subscription s = owned(customerId, id);
        if (s.getStatus() != SubscriptionStatus.CANCELLED) {
            deliveries.generateFor(customerId);
            s.cancel(time.today());
        }
        return view(s, "Subscription cancelled");
    }

    private Subscription live(Long customerId, Long id) {
        Subscription s = owned(customerId, id);
        if (s.getStatus() == SubscriptionStatus.CANCELLED) {
            throw ApiException.conflict("SUBSCRIPTION_CANCELLED", "This subscription was cancelled. Subscribe again instead");
        }
        return s;
    }

    private Subscription owned(Long customerId, Long id) {
        return subscriptions.findById(id)
                .filter(s -> s.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Subscription " + id));
    }

    private void requireCustomer(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw ApiException.notFound("Customer " + customerId);
        }
    }

    private SubscriptionView view(Subscription s, String message) {
        Product p = catalog.product(s.getProductId());
        BigDecimal price = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
        BigDecimal monthly = price.multiply(BigDecimal.valueOf((long) s.getQuantity() * s.getFrequency().deliveriesPerMonth()));
        return new SubscriptionView(s.getId(), p.getId(), p.getName(), p.getUnit(), price, catalog.imageUrl(p),
                s.getQuantity(), s.getFrequency().name(), s.getStatus().name(), s.getStartDate(),
                s.nextDelivery(time.today()), s.getEndDate(), s.getAddressId(), s.getDeliveryAddress(), monthly, message);
    }
}
