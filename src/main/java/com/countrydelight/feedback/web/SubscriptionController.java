package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.service.SubscriptionService;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.SubscribeRequest;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.SubscriptionList;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.SubscriptionView;
import com.countrydelight.feedback.web.dto.SubscriptionDtos.UpdateSubscriptionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in household's subscriptions. */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping
    public SubscriptionList list(@CurrentCustomer Long customerId) {
        return subscriptions.list(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView subscribe(@CurrentCustomer Long customerId, @Valid @RequestBody SubscribeRequest body) {
        return subscriptions.subscribe(customerId, body);
    }

    @PatchMapping("/{subscriptionId}")
    public SubscriptionView update(@CurrentCustomer Long customerId, @PathVariable Long subscriptionId,
                                   @Valid @RequestBody UpdateSubscriptionRequest body) {
        return subscriptions.update(customerId, subscriptionId, body);
    }

    @PostMapping("/{subscriptionId}/pause")
    public SubscriptionView pause(@CurrentCustomer Long customerId, @PathVariable Long subscriptionId) {
        return subscriptions.pause(customerId, subscriptionId);
    }

    @PostMapping("/{subscriptionId}/resume")
    public SubscriptionView resume(@CurrentCustomer Long customerId, @PathVariable Long subscriptionId) {
        return subscriptions.resume(customerId, subscriptionId);
    }

    /** Cancel (kept in the list as CANCELLED). */
    @DeleteMapping("/{subscriptionId}")
    public SubscriptionView cancel(@CurrentCustomer Long customerId, @PathVariable Long subscriptionId) {
        return subscriptions.cancel(customerId, subscriptionId);
    }
}
