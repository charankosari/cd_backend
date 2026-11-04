package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.CartItem;
import com.countrydelight.feedback.domain.CartItemSource;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.repository.CartItemRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.FeedbackRepository;
import com.countrydelight.feedback.web.dto.CartDtos.AddToCartRequest;
import com.countrydelight.feedback.web.dto.CartDtos.AddToCartResponse;
import com.countrydelight.feedback.web.dto.CartDtos.CartItemResponse;
import com.countrydelight.feedback.web.dto.CartDtos.CartResponse;
import com.countrydelight.feedback.web.dto.CartDtos.CartSummary;
import com.countrydelight.feedback.web.dto.CartDtos.UpdateCartItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CartService {

    private static final int MAX_QTY_PER_LINE = 20;

    private final CartItemRepository cart;
    private final CustomerRepository customers;
    private final FeedbackRepository feedbacks;
    private final CatalogService catalog;
    private final TimeProvider time;
    private final FeedbackProperties props;

    public CartService(CartItemRepository cart, CustomerRepository customers, FeedbackRepository feedbacks,
                       CatalogService catalog, TimeProvider time, FeedbackProperties props) {
        this.cart = cart;
        this.customers = customers;
        this.feedbacks = feedbacks;
        this.catalog = catalog;
        this.time = time;
        this.props = props;
    }

    @Transactional
    public AddToCartResponse add(Long customerId, AddToCartRequest req) {
        requireCustomer(customerId);
        Product product = catalog.product(req.productId());
        LocalDate today = time.today();
        LocalDate date = req.deliveryDate() != null ? req.deliveryDate() : today.plusDays(1);
        checkDeliveryDate(date, today);
        int qty = req.quantity() == null ? 1 : req.quantity();

        CartItemSource source = CartItemSource.MANUAL;
        if (req.feedbackId() != null) {
            Feedback f = feedbacks.findById(req.feedbackId())
                    .filter(x -> x.getCustomerId().equals(customerId))
                    .orElseThrow(() -> ApiException.notFound("Feedback " + req.feedbackId()));
            if (!f.getProductId().equals(product.getId())) {
                throw ApiException.badRequest("PRODUCT_MISMATCH", "Feedback is for a different product");
            }
            source = CartItemSource.FEEDBACK_BUY_AGAIN;
        }

        // Same product on the same date: bump the quantity instead of adding a duplicate line.
        CartItem line = cart.findFirstByCustomerIdAndProductIdAndDeliveryDate(customerId, product.getId(), date)
                .orElse(null);
        if (line != null) {
            if (line.getQuantity() + qty > MAX_QTY_PER_LINE) {
                throw ApiException.badRequest("QUANTITY_LIMIT", "At most " + MAX_QTY_PER_LINE + " per product per day");
            }
            line.addQuantity(qty);
        } else {
            line = cart.save(new CartItem(customerId, product.getId(), qty, date, source, req.feedbackId(), time.now()));
        }
        String when = date.equals(today.plusDays(1)) ? "tomorrow" : PromptService.CARD_DATE.format(date);
        return new AddToCartResponse(toDto(line, product), product.getName() + " added for " + when, view(customerId));
    }

    @Transactional(readOnly = true)
    public CartResponse view(Long customerId) {
        requireCustomer(customerId);
        List<CartItemResponse> items = cart.findByCustomerIdOrderByDeliveryDateAscIdAsc(customerId).stream()
                .map(i -> toDto(i, catalog.product(i.getProductId())))
                .toList();
        int qty = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal total = items.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(customerId, items, qty, total);
    }

    @Transactional
    public CartResponse remove(Long customerId, Long cartItemId) {
        CartItem item = owned(customerId, cartItemId);
        cart.delete(item);
        cart.flush();
        return view(customerId);
    }

    /**
     * Change a line's quantity and/or delivery date. Moving it to a date that already has the same product
     * merges the two lines (quantities added), so the cart never shows duplicates.
     */
    @Transactional
    public AddToCartResponse update(Long customerId, Long cartItemId, UpdateCartItemRequest req) {
        if (req.quantity() == null && req.deliveryDate() == null) {
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "Send quantity and/or deliveryDate");
        }
        CartItem line = owned(customerId, cartItemId);
        Product product = catalog.product(line.getProductId());
        LocalDate today = time.today();
        int qty = req.quantity() != null ? req.quantity() : line.getQuantity();
        LocalDate date = req.deliveryDate() != null ? req.deliveryDate() : line.getDeliveryDate();
        if (req.deliveryDate() != null) {
            checkDeliveryDate(date, today);
        }

        CartItem result = line;
        CartItem other = date.equals(line.getDeliveryDate()) ? null
                : cart.findFirstByCustomerIdAndProductIdAndDeliveryDate(customerId, product.getId(), date).orElse(null);
        if (other != null) {
            if (other.getQuantity() + qty > MAX_QTY_PER_LINE) {
                throw ApiException.badRequest("QUANTITY_LIMIT", "At most " + MAX_QTY_PER_LINE + " per product per day");
            }
            other.setQuantity(other.getQuantity() + qty);
            cart.delete(line);
            result = other;
        } else {
            line.setQuantity(qty);
            line.setDeliveryDate(date);
        }
        cart.flush();
        String when = date.equals(today.plusDays(1)) ? "tomorrow" : PromptService.CARD_DATE.format(date);
        return new AddToCartResponse(toDto(result, product),
                product.getName() + ": " + result.getQuantity() + " for " + when, view(customerId));
    }

    @Transactional
    public CartResponse clear(Long customerId) {
        requireCustomer(customerId);
        cart.deleteAll(cart.findByCustomerIdOrderByDeliveryDateAscIdAsc(customerId));
        cart.flush();
        return view(customerId);
    }

    /** Small payload for the cart badge on every screen. */
    @Transactional(readOnly = true)
    public CartSummary summary(Long customerId) {
        CartResponse full = view(customerId);
        LocalDate next = full.items().stream().map(CartItemResponse::deliveryDate)
                .filter(d -> d.isAfter(time.today())).min(LocalDate::compareTo).orElse(null);
        return new CartSummary(full.items().size(), full.totalQuantity(), full.totalAmount(), next);
    }

    private CartItem owned(Long customerId, Long cartItemId) {
        return cart.findById(cartItemId)
                .filter(i -> i.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Cart item " + cartItemId));
    }

    /** Delivery dates: tomorrow .. today + 30 days. */
    private void checkDeliveryDate(LocalDate date, LocalDate today) {
        if (!date.isAfter(today)) {
            throw ApiException.badRequest("INVALID_DELIVERY_DATE", "Delivery date must be tomorrow or later");
        }
        LocalDate max = today.plusDays(props.buyAgainMaxDaysAhead());
        if (date.isAfter(max)) {
            throw ApiException.badRequest("INVALID_DELIVERY_DATE", "Delivery date must be on or before " + max);
        }
    }

    private CartItemResponse toDto(CartItem i, Product p) {
        BigDecimal price = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
        return new CartItemResponse(i.getId(), p.getId(), p.getName(), p.getUnit(), price, i.getQuantity(),
                price.multiply(BigDecimal.valueOf(i.getQuantity())), i.getDeliveryDate(),
                i.getSource().name(), i.getFeedbackId(), catalog.imageUrl(p));
    }

    private void requireCustomer(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw ApiException.notFound("Customer " + customerId);
        }
    }
}
