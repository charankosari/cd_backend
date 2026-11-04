package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CartItem;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
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

/** Dashboard "Orders" screen: all customers' orders, and the "Mark delivered" action. */
@Service
public class DashboardOrderService {

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final CatalogService catalog;
    private final DeliveryService deliveries;
    private final CartItemRepository cartItems;
    private final TimeProvider time;

    public DashboardOrderService(OrderRepository orders, CustomerRepository customers, CatalogService catalog,
                                 DeliveryService deliveries, CartItemRepository cartItems, TimeProvider time) {
        this.orders = orders;
        this.customers = customers;
        this.catalog = catalog;
        this.deliveries = deliveries;
        this.cartItems = cartItems;
        this.time = time;
    }

    /**
     * status = SCHEDULED | DELIVERED | CANCELLED, or null/blank for all.
     * Scheduled orders come soonest-delivery first (what to deliver next); others newest first.
     */
    @Transactional
    public OrdersPage list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw ApiException.badRequest("BAD_PAGE", "page >= 0 and 1 <= size <= 200");
        }
        deliveries.markAllDueOrdersDelivered();        // orders whose day has come show as DELIVERED

        OrderStatus filter = parseStatus(status);
        Sort sort = filter == OrderStatus.SCHEDULED
                ? Sort.by(Sort.Order.asc("deliveryDate"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("deliveryDate"), Sort.Order.desc("id"));
        PageRequest pageable = PageRequest.of(page, size, sort);
        Page<CustomerOrder> result = filter == null ? orders.findAll(pageable) : orders.findByStatus(filter, pageable);

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

    /**
     * What customers currently have in their carts. Only lines for tomorrow or later: a line whose date has passed
     * was never checked out, so it's no longer a live cart.
     */
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
            List<CartLine> items = e.getValue().stream().map(i -> {
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
                o.getDeliveryAddress());
    }

    private OrderLine line(OrderItem i, Product p) {
        BigDecimal price = i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice();
        return new OrderLine(i.getId(), i.getProductId(), p == null ? i.getProductId() : p.getName(),
                p == null ? null : p.getUnit(), i.getQuantity(), price.multiply(BigDecimal.valueOf(i.getQuantity())),
                i.getPurchaseType().name(), catalog.imageUrl(p));
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
