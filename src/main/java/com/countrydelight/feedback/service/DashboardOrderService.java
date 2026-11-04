package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CartItem;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.SubscriptionRow;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.SubscriptionsPage;
import java.time.LocalDate;
import com.countrydelight.feedback.repository.CartItemRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.OrderRepository;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.CartLine;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.CartsOverview;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.CustomerCart;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.DeliverResponse;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.OrderLine;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.OrderRow;
import com.countrydelight.feedback.web.dto.DashboardOrderDtos.OrdersPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardOrderService {

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final CatalogService catalog;
    private final DeliveryService deliveries;
    private final CartItemRepository cartItems;
    private final TimeProvider time;
    private final SubscriptionRepository subscriptionRepo;

    public DashboardOrderService(OrderRepository orders, CustomerRepository customers, CatalogService catalog,
                                 DeliveryService deliveries, CartItemRepository cartItems, TimeProvider time,
                                 SubscriptionRepository subscriptionRepo) {
        this.orders = orders;
        this.customers = customers;
        this.catalog = catalog;
        this.deliveries = deliveries;
        this.cartItems = cartItems;
        this.time = time;
        this.subscriptionRepo = subscriptionRepo;
    }

    @Transactional
    public OrdersPage list(String status, String type, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw ApiException.badRequest("BAD_PAGE", "page >= 0 and 1 <= size <= 200");
        }
        deliveries.markAllDueOrdersDelivered();

        OrderStatus filter = parseStatus(status);
        PurchaseType typeFilter = parseType(type);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id")));
        Page<CustomerOrder> result = orders.search(filter, typeFilter, pageable);

        Map<Long, Customer> people = customers.findAllById(
                        result.getContent().stream().map(CustomerOrder::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<String, Product> products = catalog.allProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), orders.countByStatus(s));
        }
        counts.put("ALL", orders.count());
        counts.put("ONE_TIME", orders.countContaining(PurchaseType.ONE_TIME));
        counts.put("SUBSCRIPTION", orders.countContaining(PurchaseType.SUBSCRIPTION));

        List<OrderRow> rows = result.getContent().stream().map(o -> row(o, people, products)).toList();
        return new OrdersPage(rows, page, size, result.getTotalElements(), result.getTotalPages(), counts);
    }

    @Transactional
    public DeliverResponse deliver(Long orderId) {
        CustomerOrder order = deliveries.deliverNow(orderId);
        Map<Long, Customer> people = customers.findById(order.getCustomerId())
                .map(c -> Map.of(c.getId(), c)).orElse(Map.of());
        Map<String, Product> products = catalog.allProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return new DeliverResponse(row(order, people, products),
                "Order #" + orderId + " marked delivered on " + PromptService.CARD_DATE.format(order.getDeliveryDate()));
    }

    @Transactional
    public SubscriptionsPage subscriptions(String status, String productId) {
        deliveries.markAllDueOrdersDelivered();
        SubscriptionStatus filter = parseSubscriptionStatus(status);
        String product = productId == null || productId.isBlank() ? null : productId.trim().toUpperCase();
        LocalDate today = time.today();
        List<Subscription> all = subscriptionRepo.findAll();
        Map<String, Product> products = catalog.allProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Customer> people = customers.findAllById(all.stream().map(Subscription::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        List<SubscriptionRow> rows = all.stream()
                .filter(s -> filter == null || s.getStatus() == filter)
                .filter(s -> product == null || s.getProductId().equals(product))
                .sorted(java.util.Comparator.comparing(Subscription::getId, java.util.Comparator.reverseOrder()))
                .map(s -> {
                    Product p = products.get(s.getProductId());
                    Customer c = people.get(s.getCustomerId());
                    BigDecimal price = p == null || p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
                    return new SubscriptionRow(s.getId(), s.getCustomerId(), c == null ? null : c.getName(),
                            s.getProductId(), p == null ? s.getProductId() : p.getName(), p == null ? null : p.getUnit(),
                            catalog.imageUrl(p), s.getQuantity(), s.getFrequency().name(), s.getStatus().name(),
                            s.getStartDate(), s.nextDelivery(today), s.getEndDate(), s.getDeliveryAddress(),
                            price.multiply(BigDecimal.valueOf((long) s.getQuantity() * s.getFrequency().deliveriesPerMonth())));
                }).toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        for (SubscriptionStatus st : SubscriptionStatus.values()) {
            counts.put(st.name(), all.stream().filter(s -> s.getStatus() == st
                    && (product == null || s.getProductId().equals(product))).count());
        }
        counts.put("ALL", all.stream().filter(s -> product == null || s.getProductId().equals(product)).count());
        BigDecimal activeValue = all.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .filter(s -> product == null || s.getProductId().equals(product))
                .map(s -> {
                    Product p = products.get(s.getProductId());
                    BigDecimal price = p == null || p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
                    return price.multiply(BigDecimal.valueOf((long) s.getQuantity() * s.getFrequency().deliveriesPerMonth()));
                }).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SubscriptionsPage(rows, counts, activeValue);
    }

    private static SubscriptionStatus parseSubscriptionStatus(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return SubscriptionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_STATUS", "status must be ACTIVE, PAUSED, CANCELLED or ALL");
        }
    }

    @Transactional(readOnly = true)
    public CartsOverview carts() {
        List<CartItem> lines = cartItems.findByDeliveryDateAfterOrderByCustomerIdAscDeliveryDateAscIdAsc(time.today());
        Map<String, Product> products = catalog.allProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Customer> people = customers.findAllById(
                        lines.stream().map(CartItem::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        Map<Long, List<CartItem>> byCustomer = lines.stream()
                .collect(Collectors.groupingBy(CartItem::getCustomerId, LinkedHashMap::new, Collectors.toList()));
        List<CustomerCart> carts = byCustomer.entrySet().stream().map(e -> {
            Customer c = people.get(e.getKey());
            List<CartLine> items = e.getValue().stream()
                    .sorted(java.util.Comparator.comparing(CartItem::getCreatedAt,
                                    java.util.Comparator.nullsLast(java.util.Comparator.<java.time.Instant>reverseOrder()))
                            .thenComparing(CartItem::getId, java.util.Comparator.reverseOrder()))
                    .map(i -> {
                Product p = products.get(i.getProductId());
                BigDecimal price = p == null || p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
                return new CartLine(i.getId(), i.getProductId(), p == null ? i.getProductId() : p.getName(),
                        p == null ? null : p.getUnit(), i.getQuantity(), price.multiply(BigDecimal.valueOf(i.getQuantity())),
                        i.getDeliveryDate(), i.getSource() == null ? null : i.getSource().name(), catalog.imageUrl(p));
            }).toList();
            return new CustomerCart(e.getKey(), c == null ? null : c.getName(), c == null ? null : c.getArea(),
                    c == null ? null : c.getCity(), items.stream().mapToInt(CartLine::quantity).sum(),
                    items.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                    e.getValue().stream().map(CartItem::getCreatedAt).filter(java.util.Objects::nonNull)
                            .max(java.time.Instant::compareTo).orElse(null),
                    items);
        }).sorted(java.util.Comparator.comparing(CustomerCart::lastAddedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))).toList();

        return new CartsOverview(carts, carts.size(), lines.size(),
                carts.stream().mapToInt(CustomerCart::totalQuantity).sum(),
                carts.stream().map(CustomerCart::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private OrderRow row(CustomerOrder o, Map<Long, Customer> people, Map<String, Product> products) {
        List<OrderLine> lines = o.getItems().stream().map(i -> line(i, products.get(i.getProductId()))).toList();
        Customer c = people.get(o.getCustomerId());
        return new OrderRow(o.getId(), o.getCustomerId(), c == null ? null : c.getName(), o.getArea(), o.getCity(),
                o.getDeliveryDate(), o.getStatus().name(), o.getStatus() == OrderStatus.SCHEDULED,
                lines.stream().mapToInt(OrderLine::quantity).sum(),
                lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add), lines,
                o.getDeliveryAddress(), orderType(lines), o.getPlacedAt());
    }

    private OrderLine line(OrderItem i, Product p) {
        BigDecimal price = i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice();
        return new OrderLine(i.getId(), i.getProductId(), p == null ? i.getProductId() : p.getName(),
                p == null ? null : p.getUnit(), i.getQuantity(), price.multiply(BigDecimal.valueOf(i.getQuantity())),
                i.getPurchaseType().name(), catalog.imageUrl(p));
    }

    private static String orderType(List<OrderLine> lines) {
        boolean sub = lines.stream().anyMatch(l -> "SUBSCRIPTION".equals(l.purchaseType()));
        boolean once = lines.stream().anyMatch(l -> "ONE_TIME".equals(l.purchaseType()));
        return sub && once ? "MIXED" : sub ? "SUBSCRIPTION" : "ONE_TIME";
    }

    private static PurchaseType parseType(String type) {
        if (type == null || type.isBlank() || type.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return PurchaseType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_TYPE", "type must be ONE_TIME, SUBSCRIPTION or ALL");
        }
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_STATUS", "status must be SCHEDULED, DELIVERED, CANCELLED or ALL");
        }
    }
}
