package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.service.OrderService;
import com.countrydelight.feedback.web.dto.OrderDtos.OrderView;
import com.countrydelight.feedback.web.dto.OrderDtos.PlaceOrderRequest;
import com.countrydelight.feedback.web.dto.OrderDtos.PlaceOrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Checkout: turn the cart into orders (one per delivery date). Body is optional. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceOrderResponse place(@CurrentCustomer Long customerId,
                                    @RequestBody(required = false) PlaceOrderRequest body) {
        return orders.placeFromCart(customerId, body == null ? null : body.deliveryDate(),
                body == null ? null : body.addressId());
    }

    /** My orders, newest first, with each item's feedback state. */
    @GetMapping
    public List<OrderView> list(@CurrentCustomer Long customerId) {
        return orders.list(customerId);
    }

    @GetMapping("/{orderId}")
    public OrderView get(@CurrentCustomer Long customerId, @PathVariable Long orderId) {
        return orders.get(customerId, orderId);
    }
}
