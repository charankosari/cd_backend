package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.domain.FeedbackPrompt;
import com.countrydelight.feedback.domain.OrderItem;
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
    private final TimeProvider time;
    private final FeedbackProperties props;

    public PromptService(EligibilityEngine engine, FeedbackPromptRepository prompts, FeedbackRepository feedbacks,
                         OrderItemRepository orderItems, SubscriptionRepository subscriptions,
                         CustomerRepository customers, CatalogService catalog, TimeProvider time,
                         FeedbackProperties props) {
        this.engine = engine;
        this.prompts = prompts;
        this.feedbacks = feedbacks;
        this.orderItems = orderItems;
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.catalog = catalog;
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
        LocalDate today = time.today();
        Instant now = time.now();

        // 1. A card is already out there: keep showing the same one until it is answered / skipped / ignored.
        Optional<FeedbackPrompt> pendingOpt = prompts.findFirstByCustomerIdAndStatusOrderByIdDesc(customerId, PromptStatus.PENDING);
        if (pendingOpt.isPresent()) {
            FeedbackPrompt pending = pendingOpt.get();
            if (subscriptions.isSubscribed(customerId, pending.getProductId(), today)) {
                pending.resolve(PromptStatus.CANCELLED, today, now);
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
        Optional<LocalDate> last = prompts.findByCustomerIdOrderByIdAsc(customerId).stream()
                .filter(p -> COUNTS_FOR_CAP.contains(p.getStatus()) && p.getResolvedOn() != null)
                .map(FeedbackPrompt::getResolvedOn)
                .max(Comparator.naturalOrder());
        LocalDate next = last.map(d -> d.plusDays(props.userCooldownDays())).orElse(null);
        return new FrequencyCap(last.orElse(null), next, next != null && today.isBefore(next));
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
                "/api/cart/items");
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
        return new PromptCard(p.getId(), product.getId(), product.getName(), product.getUnit(), product.getImageUrl(),
                "How was your " + product.getName() + "?",
                "Delivered on " + CARD_DATE.format(p.getDeliveryDate()),
                p.getOrderId(), p.getDeliveryDate(), p.getExpiresOn(),
                p.reasonList().stream().map(PromptReason::name).toList(),
                1, 5, props.highRatingThreshold(), "Skip");
    }
}
