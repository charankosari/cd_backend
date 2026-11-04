package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.domain.FeedbackPrompt;
import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.PromptSource;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PromptReason;
import com.countrydelight.feedback.domain.PromptStatus;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.FeedbackPromptRepository;
import com.countrydelight.feedback.repository.FeedbackRepository;
import com.countrydelight.feedback.repository.OrderItemRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import com.countrydelight.feedback.service.EligibilityEngine.Candidate;
import com.countrydelight.feedback.service.EligibilityEngine.Evaluation;
import com.countrydelight.feedback.web.dto.PromptDtos.BuyAgainOffer;
import com.countrydelight.feedback.web.dto.PromptDtos.IssueForm;
import com.countrydelight.feedback.web.dto.PromptDtos.PromptCard;
import com.countrydelight.feedback.web.dto.PromptDtos.PromptResponse;
import com.countrydelight.feedback.web.dto.PromptDtos.QuickDate;
import com.countrydelight.feedback.web.dto.PromptDtos.RatingResponse;
import com.countrydelight.feedback.web.dto.PromptDtos.SkipResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The in-app feedback card lifecycle: decide what (if anything) to show, take the one-tap rating,
 * route to buy-again or issue capture, and handle skip.
 */
@Service
public class PromptService {

    static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);

    /** Interactions that consume the user's frequency cap. CANCELLED (system withdrew it) does not. */
    private static final Set<PromptStatus> COUNTS_FOR_CAP =
            EnumSet.of(PromptStatus.ANSWERED, PromptStatus.SKIPPED, PromptStatus.IGNORED);

    public record FrequencyCap(LocalDate lastInteractionOn, LocalDate nextEligibleOn, boolean capped) {
    }

    private final EligibilityEngine engine;
    private final FeedbackPromptRepository prompts;
    private final FeedbackRepository feedbacks;
    private final OrderItemRepository orderItems;
    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final CatalogService catalog;
    private final DeliveryService deliveries;
    private final TimeProvider time;
    private final FeedbackProperties props;

    public PromptService(EligibilityEngine engine, FeedbackPromptRepository prompts, FeedbackRepository feedbacks,
                         OrderItemRepository orderItems, SubscriptionRepository subscriptions,
                         CustomerRepository customers, CatalogService catalog, DeliveryService deliveries,
                         TimeProvider time, FeedbackProperties props) {
        this.engine = engine;
        this.prompts = prompts;
        this.feedbacks = feedbacks;
        this.orderItems = orderItems;
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.catalog = catalog;
        this.deliveries = deliveries;
        this.time = time;
        this.props = props;
    }

    /**
     * Called every time the home screen loads. Returns at most ONE card.
     * Order of checks: existing pending card, then frequency cap, then pick the best new candidate.
     */
    @Transactional
    public PromptResponse nextPrompt(Long customerId) {
        requireCustomer(customerId);
        deliveries.markDueOrdersDelivered(customerId);
        LocalDate today = time.today();
        Instant now = time.now();

        // 1. A card is already out there: keep showing the same one until it is answered / skipped / ignored.
        //    Only cards WE started (AUTO). A "Rate now" the user opened and left isn't pushed onto the home screen.
        Optional<FeedbackPrompt> pendingOpt = prompts.findFirstByCustomerIdAndStatusAndSourceOrderByIdDesc(
                customerId, PromptStatus.PENDING, PromptSource.AUTO);
        if (pendingOpt.isPresent()) {
            FeedbackPrompt pending = pendingOpt.get();
            if (subscriptions.isSubscribed(customerId, pending.getProductId(), today)
                    || alreadyReviewed(customerId, pending.getProductId(), pending.getId())) {
                pending.resolve(PromptStatus.CANCELLED, today, now);   // subscribed, or reviewed via "Rate now"
            } else if (isIgnored(pending, today)) {
                // Count the cap from the last day the user actually saw it.
                pending.resolve(PromptStatus.IGNORED, pending.getLastShownOn(), now);
            } else {
                pending.recordImpression(today);
                return PromptResponse.shown(toCard(pending));
            }
        }

        // 2. Frequency cap across all products.
        FrequencyCap cap = frequencyCap(customerId, today);
        if (cap.capped()) {
            return PromptResponse.hidden("FREQUENCY_CAP",
                    "Asked recently; next feedback ask possible on " + cap.nextEligibleOn(), cap.nextEligibleOn());
        }

        // 3. Pick the best candidate.
        Evaluation evaluation = engine.evaluate(customerId, today);
        if (evaluation.rankedCandidates().isEmpty()) {
            return PromptResponse.hidden("NOTHING_TO_ASK", "No delivery is due for feedback today", null);
        }
        Candidate best = evaluation.rankedCandidates().get(0);
        OrderItem item = best.item();
        FeedbackPrompt prompt = new FeedbackPrompt(customerId, best.product().getId(), item.getId(),
                item.getOrder().getId(), item.deliveryDate(), best.dueOn(), best.expiresOn(),
                item.getOrder().getArea(), item.getOrder().getCity(), best.reasons(), best.score(), now);
        prompt.recordImpression(today);
        prompts.save(prompt);
        return PromptResponse.shown(toCard(prompt));
    }

    private boolean isIgnored(FeedbackPrompt p, LocalDate today) {
        boolean windowOver = today.isAfter(p.getExpiresOn());
        boolean seenEnough = !today.equals(p.getLastShownOn()) && p.getShownDays() >= props.maxImpressionDays();
        return windowOver || seenEnough;
    }

    @Transactional(readOnly = true)
    public FrequencyCap frequencyCap(Long customerId, LocalDate today) {
        // Only asks WE made count. Ratings the user chose to give ("Rate now") don't use up the cap.
        Optional<LocalDate> last = prompts.findByCustomerIdOrderByIdAsc(customerId).stream()
                .filter(p -> !p.isUserInitiated())
                .filter(p -> COUNTS_FOR_CAP.contains(p.getStatus()) && p.getResolvedOn() != null)
                .map(FeedbackPrompt::getResolvedOn)
                .max(Comparator.naturalOrder());
        LocalDate next = last.map(d -> d.plusDays(props.userCooldownDays())).orElse(null);
        return new FrequencyCap(last.orElse(null), next, next != null && today.isBefore(next));
    }

    /**
     * "Rate now": the user doesn't want to wait X days. Opens a card for this delivered item straight away,
     * with no waiting period, no frequency cap and no priority ranking. Then use the normal rating/skip endpoints.
     */
    @Transactional
    public PromptResponse rateNow(Long customerId, Long orderItemId) {
        requireCustomer(customerId);
        deliveries.markDueOrdersDelivered(customerId);
        LocalDate today = time.today();

        OrderItem item = orderItems.findWithOrder(orderItemId)
                .filter(i -> i.getOrder().getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Order item " + orderItemId));
        if (item.getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.conflict("NOT_DELIVERED",
                    "You can rate this once it's delivered (" + CARD_DATE.format(item.deliveryDate()) + ")");
        }
        Product product = catalog.product(item.getProductId());
        if (product.getFeedbackDelayDays() == null) {
            throw ApiException.badRequest("FEEDBACK_NOT_AVAILABLE", "We don't collect feedback for " + product.getName());
        }

        FeedbackPrompt prompt = prompts.findByOrderItemId(orderItemId).orElse(null);
        if (prompt != null && prompt.getStatus() == PromptStatus.ANSWERED) {
            throw ApiException.conflict("ALREADY_RATED", "You've already rated this delivery");
        }
        if (alreadyReviewed(customerId, product.getId(), prompt == null ? null : prompt.getId())) {
            throw alreadyReviewedError(product);
        }
        if (prompt == null) {
            prompt = new FeedbackPrompt(customerId, product.getId(), item.getId(), item.getOrder().getId(),
                    item.deliveryDate(), today, today.plusDays(props.windowDays()),
                    item.getOrder().getArea(), item.getOrder().getCity(),
                    List.of(PromptReason.USER_REQUESTED), PromptReason.USER_REQUESTED.weight(), time.now());
            prompt.openForUser();
            prompts.save(prompt);
        } else if (prompt.getStatus() == PromptStatus.ANSWERED) {
            throw ApiException.conflict("ALREADY_RATED", "You've already rated this delivery");
        } else if (prompt.getStatus() != PromptStatus.PENDING) {
            prompt.openForUser();          // skipped / ignored earlier, but now the user wants to rate it
        }
        // PENDING: return the same card (if it's the home-screen card, rating it here answers that card too)
        return PromptResponse.shown(toCard(prompt));
    }

    /** One tap = one rating. Saves immediately, then tells the app which screen comes next. */
    @Transactional
    public RatingResponse submitRating(Long customerId, Long promptId, int rating) {
        FeedbackPrompt prompt = ownedPrompt(customerId, promptId);
        switch (prompt.getStatus()) {
            case ANSWERED -> throw ApiException.conflict("ALREADY_RATED", "This delivery has already been rated");
            case SKIPPED, CANCELLED -> throw ApiException.conflict("PROMPT_CLOSED",
                    "This feedback request is closed (" + prompt.getStatus() + ")");
            default -> {
                // PENDING, or IGNORED but the user still had the card on screen: a late answer is still useful.
            }
        }
        if (alreadyReviewed(customerId, prompt.getProductId(), prompt.getId())) {
            throw alreadyReviewedError(catalog.product(prompt.getProductId()));
        }
        LocalDate today = time.today();
        Instant now = time.now();
        OrderItem item = orderItems.findWithOrder(prompt.getOrderItemId())
                .orElseThrow(() -> ApiException.notFound("Order item " + prompt.getOrderItemId()));
        Product product = catalog.product(prompt.getProductId());

        Feedback feedback = feedbacks.save(new Feedback(prompt, product.getName(), item.getPurchaseType(), rating, now, today));
        prompt.resolve(PromptStatus.ANSWERED, today, now);

        if (rating >= props.highRatingThreshold()) {
            if (subscriptions.isSubscribed(customerId, product.getId(), today)) {
                return new RatingResponse(feedback.getId(), promptId, rating, "THANK_YOU",
                        "Thanks for rating! Glad you liked it.", null, null);
            }
            return new RatingResponse(feedback.getId(), promptId, rating, "BUY_AGAIN",
                    "Glad you liked it! Want it again?", buyAgainOffer(product, item.getQuantity(), feedback.getId(), today), null);
        }
        IssueForm form = new IssueForm(
                "What went wrong with your " + product.getName() + "?",
                catalog.issueOptions(product.getId()),
                true, 1, true, props.noteMaxLength(), true,
                "/api/feedback/" + feedback.getId() + "/issues",
                "/api/feedback/" + feedback.getId() + "/photo");
        return new RatingResponse(feedback.getId(), promptId, rating, "ISSUE_DETAILS",
                "Sorry about that. Tell us what went wrong.", null, form);
    }

    private BuyAgainOffer buyAgainOffer(Product product, int quantity, Long feedbackId, LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        return new BuyAgainOffer(product.getId(), product.getName(), product.getUnit(), product.getPrice(),
                Math.max(1, quantity), tomorrow, tomorrow, today.plusDays(props.buyAgainMaxDaysAhead()),
                List.of(new QuickDate("Tomorrow", tomorrow),
                        new QuickDate(CARD_DATE.format(today.plusDays(2)), today.plusDays(2))),
                "/api/cart/items", catalog.imageUrl(product));
    }

    @Transactional
    public SkipResponse skip(Long customerId, Long promptId) {
        FeedbackPrompt prompt = ownedPrompt(customerId, promptId);
        String productName = catalog.product(prompt.getProductId()).getName();
        switch (prompt.getStatus()) {
            case SKIPPED -> {
                // Idempotent: the app may retry.
            }
            case PENDING, IGNORED -> prompt.resolve(PromptStatus.SKIPPED, time.today(), time.now());
            default -> throw ApiException.conflict("PROMPT_CLOSED",
                    "This feedback request is already " + prompt.getStatus());
        }
        return new SkipResponse(promptId, PromptStatus.SKIPPED.name(),
                "No problem. We won't ask about " + productName + " again for a while.");
    }

    /**
     * One review per product (feedback.one-review-per-product): true if the household already rated this product
     * through another card. Always false when the setting is off.
     */
    private boolean alreadyReviewed(Long customerId, String productId, Long exceptPromptId) {
        return props.oneReviewPerProduct() && prompts.findByCustomerIdOrderByIdAsc(customerId).stream()
                .anyMatch(p -> p.getStatus() == PromptStatus.ANSWERED && p.getProductId().equals(productId)
                        && !p.getId().equals(exceptPromptId));
    }

    private static ApiException alreadyReviewedError(Product product) {
        return ApiException.conflict("ALREADY_REVIEWED", "You've already reviewed " + product.getName() + ". Thanks!");
    }

    private FeedbackPrompt ownedPrompt(Long customerId, Long promptId) {
        return prompts.findById(promptId)
                .filter(p -> p.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Feedback prompt " + promptId));
    }

    private void requireCustomer(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw ApiException.notFound("Customer " + customerId);
        }
    }

    private PromptCard toCard(FeedbackPrompt p) {
        Product product = catalog.product(p.getProductId());
        return new PromptCard(p.getId(), product.getId(), product.getName(), product.getUnit(), catalog.imageUrl(product),
                "How was your " + product.getName() + "?",
                "Delivered on " + CARD_DATE.format(p.getDeliveryDate()),
                p.getOrderId(), p.getDeliveryDate(), p.getExpiresOn(),
                p.reasonList().stream().map(PromptReason::name).toList(),
                1, 5, props.highRatingThreshold(), "Skip");
    }
}
