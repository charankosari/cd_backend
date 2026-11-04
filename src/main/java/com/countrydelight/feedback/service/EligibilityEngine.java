package com.countrydelight.feedback.service;

import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.FeedbackPrompt;
import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PromptReason;
import com.countrydelight.feedback.domain.PromptStatus;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.repository.FeedbackPromptRepository;
import com.countrydelight.feedback.repository.OrderItemRepository;
import com.countrydelight.feedback.repository.ProductRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
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
public class EligibilityEngine {

    public enum Verdict {
        ELIGIBLE,
        SUBSCRIPTION_DELIVERY,
        FEEDBACK_NOT_CONFIGURED,
        SUBSCRIBED,
        ALREADY_ASKED,
        PROMPT_PENDING_FOR_PRODUCT,
        PRODUCT_ALREADY_REVIEWED,
        PRODUCT_RECENTLY_RATED,
        PRODUCT_RECENTLY_SKIPPED_OR_IGNORED,
        NOT_DUE_YET,
        WINDOW_PASSED
    }

    public record ItemVerdict(Long orderItemId, Long orderId, String productId, LocalDate deliveryDate,
                              PurchaseType purchaseType, LocalDate dueOn, LocalDate expiresOn,
                              Verdict verdict, String detail) {
    }

    public record Candidate(OrderItem item, Product product, LocalDate dueOn, LocalDate expiresOn,
                            List<PromptReason> reasons, int score, List<Long> groupedOrderItemIds) {
    }

    public record Evaluation(LocalDate today, List<ItemVerdict> verdicts, List<Candidate> rankedCandidates) {
    }

    private static final Comparator<Candidate> RANKING = Comparator
            .comparingInt(Candidate::score).reversed()
            .thenComparing(Candidate::expiresOn)
            .thenComparing(Candidate::dueOn)
            .thenComparing(c -> c.product().getId());

    private final OrderItemRepository orderItems;
    private final SubscriptionRepository subscriptions;
    private final FeedbackPromptRepository prompts;
    private final ProductRepository products;
    private final FeedbackProperties props;

    public EligibilityEngine(OrderItemRepository orderItems, SubscriptionRepository subscriptions,
                             FeedbackPromptRepository prompts, ProductRepository products, FeedbackProperties props) {
        this.orderItems = orderItems;
        this.subscriptions = subscriptions;
        this.prompts = prompts;
        this.products = products;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Evaluation evaluate(Long customerId, LocalDate today) {
        List<OrderItem> delivered = orderItems.findDeliveredByCustomer(customerId);
        Map<String, Product> productMap = products.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<String> subscribed = subscriptions.findByCustomerId(customerId).stream()
                .filter(s -> s.coversDate(today))
                .map(Subscription::getProductId)
                .collect(Collectors.toSet());

        List<FeedbackPrompt> history = prompts.findByCustomerIdOrderByIdAsc(customerId);
        Set<Long> askedItems = history.stream().map(FeedbackPrompt::getOrderItemId).collect(Collectors.toSet());
        Set<String> pendingProducts = history.stream()
                .filter(p -> p.getStatus() == PromptStatus.PENDING && !p.isUserInitiated())
                .map(FeedbackPrompt::getProductId)
                .collect(Collectors.toSet());
        Set<String> reviewed = history.stream().filter(p -> p.getStatus() == PromptStatus.ANSWERED)
                .map(FeedbackPrompt::getProductId).collect(Collectors.toSet());
        Map<String, LocalDate> ratedUntil = new HashMap<>();
        Map<String, LocalDate> skippedUntil = new HashMap<>();
        for (FeedbackPrompt p : history) {
            if (p.getResolvedOn() == null) {
                continue;
            }
            switch (p.getStatus()) {
                case ANSWERED -> ratedUntil.merge(p.getProductId(),
                        p.getResolvedOn().plusDays(props.productCooldownAfterAnswerDays()), EligibilityEngine::later);
                case SKIPPED, IGNORED -> skippedUntil.merge(p.getProductId(),
                        p.getResolvedOn().plusDays(props.productCooldownAfterSkipDays()), EligibilityEngine::later);
                default -> {
                }
            }
        }

        List<ItemVerdict> verdicts = new ArrayList<>();
        List<OrderItem> eligible = new ArrayList<>();
        for (OrderItem item : delivered) {
            Product product = productMap.get(item.getProductId());
            LocalDate delivery = item.deliveryDate();
            LocalDate due = null;
            LocalDate expires = null;
            if (product != null && product.getFeedbackDelayDays() != null) {
                due = delivery.plusDays(product.getFeedbackDelayDays());
                expires = due.plusDays(props.windowDays());
            }
            String pid = item.getProductId();

            Verdict v;
            String detail;
            if (item.getPurchaseType() == PurchaseType.SUBSCRIPTION) {
                v = Verdict.SUBSCRIPTION_DELIVERY;
                detail = "Delivered as part of a subscription";
            } else if (due == null) {
                v = Verdict.FEEDBACK_NOT_CONFIGURED;
                detail = "No feedback delay (X) configured for this product";
            } else if (subscribed.contains(pid)) {
                v = Verdict.SUBSCRIBED;
                detail = "Household is subscribed to this product";
            } else if (askedItems.contains(item.getId())) {
                v = Verdict.ALREADY_ASKED;
                detail = "A prompt was already created for this delivery";
            } else if (pendingProducts.contains(pid)) {
                v = Verdict.PROMPT_PENDING_FOR_PRODUCT;
                detail = "Another prompt for this product is waiting for an answer";
            } else if (props.oneReviewPerProduct() && reviewed.contains(pid)) {
                v = Verdict.PRODUCT_ALREADY_REVIEWED;
                detail = "Household already reviewed this product (one review per product)";
            } else if (ratedUntil.containsKey(pid) && today.isBefore(ratedUntil.get(pid))) {
                v = Verdict.PRODUCT_RECENTLY_RATED;
                detail = "Rated recently; not asking again until " + ratedUntil.get(pid);
            } else if (skippedUntil.containsKey(pid) && today.isBefore(skippedUntil.get(pid))) {
                v = Verdict.PRODUCT_RECENTLY_SKIPPED_OR_IGNORED;
                detail = "Skipped/ignored recently; not asking again until " + skippedUntil.get(pid);
            } else if (today.isBefore(due)) {
                v = Verdict.NOT_DUE_YET;
                detail = "Will be due on " + due;
            } else if (today.isAfter(expires)) {
                v = Verdict.WINDOW_PASSED;
                detail = "Feedback window ended on " + expires;
            } else {
                v = Verdict.ELIGIBLE;
                detail = "Due since " + due + ", askable until " + expires;
                eligible.add(item);
            }
            verdicts.add(new ItemVerdict(item.getId(), item.getOrder().getId(), pid, delivery,
                    item.getPurchaseType(), due, expires, v, detail));
        }

        return new Evaluation(today, verdicts, rank(eligible, delivered, productMap));
    }

    private List<Candidate> rank(List<OrderItem> eligible, List<OrderItem> delivered, Map<String, Product> productMap) {
        Map<String, List<OrderItem>> byProduct = eligible.stream()
                .collect(Collectors.groupingBy(OrderItem::getProductId, LinkedHashMap::new, Collectors.toList()));

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<OrderItem>> e : byProduct.entrySet()) {
            String pid = e.getKey();
            List<OrderItem> group = e.getValue();
            Product product = productMap.get(pid);

            OrderItem latest = group.stream()
                    .max(Comparator.comparing(OrderItem::deliveryDate).thenComparing(OrderItem::getId))
                    .orElseThrow();
            LocalDate earliest = group.stream().map(OrderItem::deliveryDate).min(Comparator.naturalOrder()).orElseThrow();

            boolean newToHousehold = delivered.stream()
                    .noneMatch(i -> i.getProductId().equals(pid) && i.deliveryDate().isBefore(earliest));
            boolean firstTimeCustomer = delivered.stream()
                    .noneMatch(i -> i.deliveryDate().isBefore(earliest));

            List<PromptReason> reasons = new ArrayList<>();
            if (newToHousehold) {
                reasons.add(PromptReason.NEW_TO_HOUSEHOLD);
            }
            if (firstTimeCustomer) {
                reasons.add(PromptReason.FIRST_TIME_CUSTOMER);
            }
            reasons.add(PromptReason.ONE_TIME_PURCHASE);
            int score = reasons.stream().mapToInt(PromptReason::weight).sum();

            LocalDate due = latest.deliveryDate().plusDays(product.getFeedbackDelayDays());
            candidates.add(new Candidate(latest, product, due, due.plusDays(props.windowDays()), reasons, score,
                    group.stream().map(OrderItem::getId).toList()));
        }
        candidates.sort(RANKING);
        return candidates;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
