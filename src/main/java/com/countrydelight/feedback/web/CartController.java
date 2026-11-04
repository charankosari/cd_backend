package com.countrydelight.feedback.web;

import com.countrydelight.feedback.service.CartService;
import com.countrydelight.feedback.web.dto.CartDtos.AddToCartRequest;
import com.countrydelight.feedback.web.dto.CartDtos.AddToCartResponse;
import com.countrydelight.feedback.web.dto.CartDtos.CartResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.countrydelight.feedback.web.FeedbackPromptController.CUSTOMER_HEADER;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public AddToCartResponse add(@RequestHeader(CUSTOMER_HEADER) Long customerId,
                                 @Valid @RequestBody AddToCartRequest body) {
        return cart.add(customerId, body);
    }

    @GetMapping
    public CartResponse view(@RequestHeader(CUSTOMER_HEADER) Long customerId) {
        return cart.view(customerId);
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartResponse remove(@RequestHeader(CUSTOMER_HEADER) Long customerId, @PathVariable Long cartItemId) {
        return cart.remove(customerId, cartItemId);
    }
}
