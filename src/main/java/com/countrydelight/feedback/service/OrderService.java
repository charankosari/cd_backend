package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.Address;
import com.countrydelight.feedback.domain.CartItem;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.repository.CartItemRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.FeedbackRepository;
import com.countrydelight.feedback.repository.OrderRepository;
import com.countrydelight.feedback.web.dto.OrderDtos.ItemFeedback;
import com.countrydelight.feedback.web.dto.OrderDtos.OrderItemView;
import com.countrydelight.feedback.web.dto.OrderDtos.OrderView;
import com.countrydelight.feedback.web.dto.OrderDtos.PlaceOrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CartItemRepository cart;
    private final CustomerRepository customers;
    private final FeedbackRepository feedbacks;
    private final CatalogService catalog;
    private final DeliveryService deliveries;
    private final TimeProvider time;
    private final AddressService addresses;
    private final FeedbackProperties props;

    public OrderService(OrderRepository orders, CartItemRepository cart, CustomerRepository customers,
                        FeedbackRepository feedbacks, CatalogService catalog, DeliveryService deliveries,
                        TimeProvider time, AddressService addresses, FeedbackProperties props) {
        this.orders = orders;
        this.cart = cart;
        this.customers = customers;
        this.feedbacks = feedbacks;
        this.catalog = catalog;
        this.deliveries = deliveries;
        this.time = time;
        this.addresses = addresses;
        this.props = props;
    }

    @Transactional
    public PlaceOrderResponse placeFromCart(Long customerId, LocalDate onlyDate, Long addressId) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer " + customerId));
        LocalDate today = time.today();
        Address deliverTo = addresses.forDelivery(customerId, addressId).orElse(null);

        List<CartItem> lines = cart.findByCustomerIdOrderByDeliveryDateAscIdAsc(customerId).stream()
                .filter(l -> onlyDate == null || l.getDeliveryDate().equals(onlyDate))
                .toList();
        if (lines.isEmpty()) {
            throw ApiException.badRequest("CART_EMPTY",
                    onlyDate == null ? "Your cart is empty" : "No cart items for " + onlyDate);
        }
        if (lines.stream().anyMatch(l -> !l.getDeliveryDate().isAfter(today))) {
            throw ApiException.badRequest("CART_DATE_PASSED",
                    "Some cart items have a delivery date that has already passed. Pick a new date for them");
        }

        Map<LocalDate, List<CartItem>> byDate = lines.stream()
                .collect(Collectors.groupingBy(CartItem::getDeliveryDate, TreeMap::new, Collectors.toList()));
        List<CustomerOrder> created = new ArrayList<>();
        byDate.forEach((date, dayLines) -> {
            CustomerOrder order = new CustomerOrder(customerId, date, OrderStatus.SCHEDULED,
                    customer.getArea(), customer.getCity());
            if (deliverTo != null) {
                order.deliverTo(deliverTo);
            }
            order.setPlacedAt(time.now());
            for (CartItem line : dayLines) {
                order.addItem(line.getProductId(), line.getQuantity(), catalog.product(line.getProductId()).getPrice(),
                        PurchaseType.ONE_TIME);
            }
            created.add(orders.save(order));
        });
        cart.deleteAll(lines);

        String message = created.size() == 1
                ? "Order placed for " + PromptService.CARD_DATE.format(created.get(0).getDeliveryDate())
                : created.size() + " orders placed";
        Reviews reviews = reviews(customerId);
        return new PlaceOrderResponse(created.stream().map(o -> view(o, reviews, today)).toList(), message);
    }

    @Transactional
    public List<OrderView> list(Long customerId) {
        requireCustomer(customerId);
        deliveries.markDueOrdersDelivered(customerId);
        Reviews reviews = reviews(customerId);
        LocalDate today = time.today();
        return orders.findByCustomerIdOrderByPlacedAtDescIdDesc(customerId).stream()
                .map(o -> view(o, reviews, today))
                .toList();
    }

    @Transactional
    public OrderView get(Long customerId, Long orderId) {
        requireCustomer(customerId);
        deliveries.markDueOrdersDelivered(customerId);
        CustomerOrder order = orders.findById(orderId)
                .filter(o -> o.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Order " + orderId));
        return view(order, reviews(customerId), time.today());
    }


    private record Reviews(Map<Long, Feedback> byItem, Map<String, Feedback> byProduct) {
    }

    private Reviews reviews(Long customerId) {
        List<Feedback> all = feedbacks.findByCustomerId(customerId);
        Map<Long, Feedback> byItem = all.stream()
                .collect(Collectors.toMap(Feedback::getOrderItemId, Function.identity(), (a, b) -> a));
        Map<String, Feedback> byProduct = all.stream()
                .collect(Collectors.toMap(Feedback::getProductId, Function.identity(),
                        (a, b) -> a.getCreatedAt().isBefore(b.getCreatedAt()) ? a : b));
        return new Reviews(byItem, byProduct);
    }

    private OrderView view(CustomerOrder o, Reviews reviews, LocalDate today) {
        List<OrderItemView> items = o.getItems().stream()
                .map(i -> itemView(o, i, reviews.byItem().get(i.getId()), reviews.byProduct().get(i.getProductId())))
                .toList();
        int qty = items.stream().mapToInt(OrderItemView::quantity).sum();
        BigDecimal total = items.stream().map(OrderItemView::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderView(o.getId(), o.getDeliveryDate(), o.getStatus().name(), o.getArea(), o.getCity(),
                qty, total, items, o.getAddressId(), o.getDeliveryAddress(), o.getPlacedAt());
    }

    private OrderItemView itemView(CustomerOrder o, OrderItem i, Feedback feedback, Feedback productReview) {
        Product p = catalog.product(i.getProductId());
        BigDecimal price = i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice();
        return new OrderItemView(i.getId(), p.getId(), p.getName(), p.getUnit(), i.getQuantity(), price,
                price.multiply(BigDecimal.valueOf(i.getQuantity())), i.getPurchaseType().name(),
                itemFeedback(o, i, p, feedback, productReview), catalog.imageUrl(p));
    }

    private ItemFeedback itemFeedback(CustomerOrder o, OrderItem i, Product p, Feedback feedback, Feedback productReview) {
        boolean feedbackEnabled = p.getFeedbackDelayDays() != null;
        LocalDate autoAskOn = feedbackEnabled && i.getPurchaseType() == PurchaseType.ONE_TIME
                ? o.getDeliveryDate().plusDays(p.getFeedbackDelayDays()) : null;
        if (feedback != null) {
            return new ItemFeedback("RATED", false, autoAskOn, feedback.getRating(), feedback.getId());
        }
        if (!feedbackEnabled) {
            return new ItemFeedback("NOT_AVAILABLE", false, null, null, null);
        }
        if (props.oneReviewPerProduct() && productReview != null) {
            return new ItemFeedback("ALREADY_REVIEWED", false, null, productReview.getRating(), productReview.getId());
        }
        if (o.getStatus() != OrderStatus.DELIVERED) {
            return new ItemFeedback("NOT_DELIVERED", false, autoAskOn, null, null);
        }
        return new ItemFeedback("NOT_RATED", true, autoAskOn, null, null);
    }

    private void requireCustomer(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw ApiException.notFound("Customer " + customerId);
        }
    }
}
