package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.service.CartService;
import com.countrydelight.feedback.web.dto.CartDtos.AddToCartRequest;
import com.countrydelight.feedback.web.dto.CartDtos.AddToCartResponse;
import com.countrydelight.feedback.web.dto.CartDtos.CartResponse;
import com.countrydelight.feedback.web.dto.CartDtos.CartSummary;
import com.countrydelight.feedback.web.dto.CartDtos.UpdateCartItemRequest;
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

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public AddToCartResponse add(@CurrentCustomer Long customerId,
                                 @Valid @RequestBody AddToCartRequest body) {
        return cart.add(customerId, body);
    }

    @GetMapping
    public CartResponse view(@CurrentCustomer Long customerId) {
        return cart.view(customerId);
    }

    /** Line count, total quantity, total amount, next delivery date: for the cart badge. */
    @GetMapping("/summary")
    public CartSummary summary(@CurrentCustomer Long customerId) {
        return cart.summary(customerId);
    }

    /** Change quantity and/or delivery date of one line. */
    @PatchMapping("/items/{cartItemId}")
    public AddToCartResponse update(@CurrentCustomer Long customerId, @PathVariable Long cartItemId,
                                    @Valid @RequestBody UpdateCartItemRequest body) {
        return cart.update(customerId, cartItemId, body);
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartResponse remove(@CurrentCustomer Long customerId, @PathVariable Long cartItemId) {
        return cart.remove(customerId, cartItemId);
    }

    /** Empty the whole cart. */
    @DeleteMapping
    public CartResponse clear(@CurrentCustomer Long customerId) {
        return cart.clear(customerId);
    }
}
