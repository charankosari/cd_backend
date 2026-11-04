package com.countrydelight.feedback.web;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.domain.FeedbackPrompt;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.FeedbackPromptRepository;
import com.countrydelight.feedback.repository.ProductRepository;
import com.countrydelight.feedback.seed.DataSeeder;
import com.countrydelight.feedback.service.EligibilityEngine;
import com.countrydelight.feedback.service.EligibilityEngine.Evaluation;
import com.countrydelight.feedback.service.EligibilityEngine.ItemVerdict;
import com.countrydelight.feedback.service.PromptService;
import com.countrydelight.feedback.service.PromptService.FrequencyCap;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TimeProvider time;
    private final DataSeeder seeder;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final FeedbackPromptRepository prompts;
    private final EligibilityEngine engine;
    private final PromptService promptService;

    public AdminController(TimeProvider time, DataSeeder seeder, CustomerRepository customers,
                           ProductRepository products, FeedbackPromptRepository prompts,
                           EligibilityEngine engine, PromptService promptService) {
        this.time = time;
        this.seeder = seeder;
        this.customers = customers;
        this.products = products;
        this.prompts = prompts;
        this.engine = engine;
        this.promptService = promptService;
    }


    public record ClockResponse(LocalDate today, boolean overridden) {
    }

    public record ClockRequest(LocalDate date, Integer plusDays) {
    }

    @GetMapping("/clock")
    public ClockResponse clock() {
        return new ClockResponse(time.today(), time.isOverridden());
    }

    @PostMapping("/clock")
    public ClockResponse setClock(@RequestBody ClockRequest req) {
        if (req.date() != null) {
            time.setToday(req.date());
        } else if (req.plusDays() != null) {
            time.setToday(time.today().plusDays(req.plusDays()));
        } else {
            throw ApiException.badRequest("BAD_CLOCK", "Send either 'date' or 'plusDays'");
        }
        return clock();
    }

    @DeleteMapping("/clock")
    public ClockResponse resetClock() {
        time.reset();
        return clock();
    }


    @PostMapping("/reset-demo-data")
    public ClockResponse reset() {
        seeder.reseed();
        return clock();
    }

    public record DemoCustomer(Long customerId, String name, String area, String city, String scenario) {
    }

    @GetMapping("/customers")
    public List<DemoCustomer> demoCustomers() {
        return customers.findByDemoScenarioIsNotNullOrderByIdAsc().stream()
                .map(c -> new DemoCustomer(c.getId(), c.getName(), c.getArea(), c.getCity(), c.getDemoScenario()))
                .toList();
    }

    public record PromptHistory(Long promptId, String productId, Long orderItemId, LocalDate deliveryDate,
                                LocalDate dueOn, LocalDate expiresOn, String status, int shownDays,
                                LocalDate lastShownOn, LocalDate resolvedOn, List<String> reasons) {
    }

    public record CandidateView(String productId, Long orderItemId, LocalDate deliveryDate, LocalDate dueOn,
                                LocalDate expiresOn, int score, List<String> reasons, List<Long> groupedOrderItemIds) {
    }

    public record EligibilityReport(LocalDate today, Long customerId, String name, String area,
                                    FrequencyCap frequencyCap, List<PromptHistory> prompts,
                                    List<ItemVerdict> deliveredItems, List<CandidateView> rankedCandidates) {
    }

    @GetMapping("/customers/{customerId}/eligibility")
    @Transactional(readOnly = true)
    public EligibilityReport eligibility(@PathVariable Long customerId) {
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer " + customerId));
        LocalDate today = time.today();
        Evaluation ev = engine.evaluate(customerId, today);
        List<PromptHistory> history = prompts.findByCustomerIdOrderByIdAsc(customerId).stream()
                .map(AdminController::toHistory).toList();
        List<CandidateView> ranked = ev.rankedCandidates().stream()
                .map(k -> new CandidateView(k.product().getId(), k.item().getId(), k.item().deliveryDate(), k.dueOn(),
                        k.expiresOn(), k.score(), k.reasons().stream().map(Enum::name).toList(), k.groupedOrderItemIds()))
                .toList();
        return new EligibilityReport(today, c.getId(), c.getName(), c.getArea(),
                promptService.frequencyCap(customerId, today), history, ev.verdicts(), ranked);
    }

    private static PromptHistory toHistory(FeedbackPrompt p) {
        return new PromptHistory(p.getId(), p.getProductId(), p.getOrderItemId(), p.getDeliveryDate(), p.getDueOn(),
                p.getExpiresOn(), p.getStatus().name(), p.getShownDays(), p.getLastShownOn(), p.getResolvedOn(),
                p.reasonList().stream().map(Enum::name).toList());
    }


    public record DelayRequest(@NotNull @Min(0) @Max(60) Integer days) {
    }

    public record DelayResponse(String productId, Integer feedbackDelayDays) {
    }

    @PutMapping("/products/{productId}/feedback-delay")
    @Transactional
    public DelayResponse setDelay(@PathVariable String productId, @Valid @RequestBody DelayRequest req) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product " + productId));
        p.setFeedbackDelayDays(req.days());
        return new DelayResponse(p.getId(), p.getFeedbackDelayDays());
    }
}
