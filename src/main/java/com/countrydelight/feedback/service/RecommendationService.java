package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.CartItem;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.repository.CartItemRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.FeedbackRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import com.countrydelight.feedback.web.dto.RecommendationDtos.BuyAgainItem;
import com.countrydelight.feedback.web.dto.RecommendationDtos.BuyAgainResponse;
import com.countrydelight.feedback.web.dto.RecommendationDtos.CartSuggestion;
import com.countrydelight.feedback.web.dto.RecommendationDtos.CartSuggestionsResponse;
import com.countrydelight.feedback.web.dto.RecommendationDtos.ExcludedProduct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final int LOOKBACK_DAYS = 90;

    private final FeedbackRepository feedbacks;
    private final SubscriptionRepository subscriptions;
    private final CartItemRepository cart;
    private final CustomerRepository customers;
    private final CatalogService catalog;
    private final TimeProvider time;
    private final FeedbackProperties props;
    private final ProductPairings pairings;

    public RecommendationService(FeedbackRepository feedbacks, SubscriptionRepository subscriptions,
                                 CartItemRepository cart, CustomerRepository customers, CatalogService catalog,
                                 TimeProvider time, FeedbackProperties props, ProductPairings pairings) {
        this.feedbacks = feedbacks;
        this.subscriptions = subscriptions;
        this.cart = cart;
        this.customers = customers;
        this.catalog = catalog;
        this.time = time;
        this.props = props;
        this.pairings = pairings;
    }

    private static final int MAX_CART_SUGGESTIONS = 4;

    @Transactional(readOnly = true)
    public CartSuggestionsResponse forCart(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw ApiException.notFound("Customer " + customerId);
        }
        LocalDate today = time.today();
        List<CartItem> lines = cart.findByCustomerIdOrderByDeliveryDateAscIdAsc(customerId).stream()
                .filter(l -> l.getDeliveryDate().isAfter(today))
                .toList();
        if (lines.isEmpty()) {
            return new CartSuggestionsResponse(List.of(), List.of());
        }
        Set<String> inCart = lines.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<String, Feedback> latest = latestRatings(customerId, today.minusDays(LOOKBACK_DAYS));
        Map<String, Product> products = catalog.allProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<String, Integer> score = new LinkedHashMap<>();
        Map<String, Integer> bestRank = new HashMap<>();
        Map<String, List<String>> pairsWith = new HashMap<>();
        Map<String, LocalDate> dateFor = new HashMap<>();
        for (CartItem line : lines) {
            List<String> partners = pairings.goesWellWith(line.getProductId());
            for (int rank = 0; rank < partners.size(); rank++) {
                String p = partners.get(rank);
                score.merge(p, 1, Integer::sum);
                bestRank.merge(p, rank, Math::min);
                pairsWith.computeIfAbsent(p, k -> new ArrayList<>());
                if (!pairsWith.get(p).contains(line.getProductId())) {
                    pairsWith.get(p).add(line.getProductId());
                }
                dateFor.putIfAbsent(p, line.getDeliveryDate());
            }
        }

        List<CartSuggestion> items = new ArrayList<>();
        List<ExcludedProduct> excluded = new ArrayList<>();
        score.keySet().stream()
                .filter(products::containsKey)
                .sorted(Comparator.comparing((String p) -> -score.get(p))
                        .thenComparing(p -> liked(latest.get(p)) ? 0 : 1)
                        .thenComparing(bestRank::get)
                        .thenComparing(p -> p))
                .forEach(p -> {
                    Product product = products.get(p);
                    Feedback rated = latest.get(p);
                    Integer rating = rated == null ? null : rated.getRating();
                    if (inCart.contains(p)) {
                        excluded.add(new ExcludedProduct(p, product.getName(), rating, "IN_CART"));
                    } else if (rated != null && !liked(rated)) {
                        excluded.add(new ExcludedProduct(p, product.getName(), rating, "RATED_LOW"));
                    } else if (subscriptions.isSubscribed(customerId, p, today)) {
                        excluded.add(new ExcludedProduct(p, product.getName(), rating, "SUBSCRIBED"));
                    } else if (items.size() < MAX_CART_SUGGESTIONS) {
                        List<String> with = pairsWith.get(p);
                        String names = with.stream().limit(2)
                                .map(id -> products.containsKey(id) ? products.get(id).getName() : id)
                                .collect(Collectors.joining(" and "));
                        String reason = "Goes well with " + names + (liked(rated) ? " · you rated it " + rating + "★" : "");
                        items.add(new CartSuggestion(p, product.getName(), product.getUnit(), product.getPrice(),
                                catalog.imageUrl(product), with, liked(rated) ? rating : null, reason,
                                dateFor.get(p), "/api/cart/items"));
                    }
                });
        return new CartSuggestionsResponse(items, excluded);
    }

    private boolean liked(Feedback f) {
        return f != null && f.getRating() >= props.highRatingThreshold();
    }

    private Map<String, Feedback> latestRatings(Long customerId, LocalDate since) {
        return feedbacks.findByCustomerId(customerId).stream()
                .filter(f -> !f.getCreatedOn().isBefore(since))
                .collect(Collectors.toMap(Feedback::getProductId, Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    @Transactional(readOnly = true)
    public BuyAgainResponse buyAgain(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw ApiException.notFound("Customer " + customerId);
        }
        LocalDate today = time.today();
        LocalDate since = today.minusDays(LOOKBACK_DAYS);

        Map<String, Feedback> latest = latestRatings(customerId, since);
        Set<String> inCart = cart.findByCustomerIdOrderByDeliveryDateAscIdAsc(customerId).stream()
                .map(CartItem::getProductId).collect(Collectors.toSet());
        Map<String, Product> products = catalog.allProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<BuyAgainItem> items = new ArrayList<>();
        List<ExcludedProduct> excluded = new ArrayList<>();
        latest.values().stream()
                .sorted(Comparator.comparing(Feedback::getRating).reversed()
                        .thenComparing(Feedback::getCreatedAt, Comparator.reverseOrder()))
                .forEach(f -> {
                    Product p = products.get(f.getProductId());
                    if (p == null) {
                        return;
                    }
                    if (f.getRating() < props.highRatingThreshold()) {
                        excluded.add(new ExcludedProduct(p.getId(), p.getName(), f.getRating(), "RATED_LOW"));
                    } else if (subscriptions.isSubscribed(customerId, p.getId(), today)) {
                        excluded.add(new ExcludedProduct(p.getId(), p.getName(), f.getRating(), "SUBSCRIBED"));
                    } else if (inCart.contains(p.getId())) {
                        excluded.add(new ExcludedProduct(p.getId(), p.getName(), f.getRating(), "IN_CART"));
                    } else {
                        items.add(new BuyAgainItem(p.getId(), p.getName(), p.getUnit(), p.getPrice(), catalog.imageUrl(p),
                                f.getRating(), f.getCreatedOn(), f.getId(), "You rated it " + f.getRating() + "★",
                                today.plusDays(1), "/api/cart/items"));
                    }
                });
        return new BuyAgainResponse(items, excluded);
    }
}
