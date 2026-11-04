package com.countrydelight.feedback;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.OrderRepository;
import com.countrydelight.feedback.repository.ProductRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import com.countrydelight.feedback.seed.DataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Buy-again rules: >= 4★ recommended, latest rating wins, subscribed / in-cart / low-rated left out. */
@SpringBootTest(properties = {"app.seed.enabled=false"})
@AutoConfigureMockMvc
class RecommendationRulesTest {

    static final LocalDate T = LocalDate.of(2026, 11, 10);
    static final long C = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TimeProvider time;
    @Autowired DataSeeder seeder;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;
    @Autowired OrderRepository orders;
    @Autowired SubscriptionRepository subscriptions;

    @BeforeEach
    void setUp() {
        seeder.wipe();
        time.setToday(T);
        products.save(new Product("PANEER", "Paneer", "200 g", new BigDecimal("95"), 1, null));
        products.save(new Product("GHEE", "Ghee", "500 ml", new BigDecimal("375"), 7, null));
        products.save(new Product("BREAD", "Bread", "400 g", new BigDecimal("50"), 2, null));
        customers.insertWithId(C, "Test", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }

    @Test
    @DisplayName("Exactly 4★ is recommended; 3★ is left out as RATED_LOW")
    void fourInThreeOut() throws Exception {
        rate(deliver("PANEER"), 4);
        rate(deliver("BREAD"), 3);
        JsonNode rec = call(get("/api/recommendations/buy-again"), null, 200);
        assertThat(rec.path("items").size()).isEqualTo(1);
        assertThat(rec.path("items").get(0).path("productId").asText()).isEqualTo("PANEER");
        assertThat(rec.path("items").get(0).path("suggestedDate").asText()).isEqualTo(T.plusDays(1).toString());
        assertThat(rec.path("excluded").get(0).path("reason").asText()).isEqualTo("RATED_LOW");
    }

    @Test
    @DisplayName("Reordered paneer can't be reviewed again; its first 5★ review keeps it recommended")
    void latestRatingWins() throws Exception {
        // One review per product: a reordered paneer can't be rated again, so the first 5★ keeps it recommended.
        rate(deliver("PANEER"), 5);
        time.setToday(T.plusDays(1));
        long again = deliver("PANEER");
        JsonNode refused = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + again + "}", 409);
        assertThat(refused.path("code").asText()).isEqualTo("ALREADY_REVIEWED");
        JsonNode rec = call(get("/api/recommendations/buy-again"), null, 200);
        assertThat(rec.path("items").get(0).path("productId").asText()).isEqualTo("PANEER");
        assertThat(rec.path("items").get(0).path("rating").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("Subscribed and in-cart products are left out; adding to cart from buy-again works with feedbackId")
    void subscribedAndInCart() throws Exception {
        rate(deliver("PANEER"), 5);
        long gheeFeedback = rate(deliver("GHEE"), 5);
        subscriptions.save(new Subscription(C, "PANEER", SubscriptionStatus.PAUSED, T.minusDays(1), null));

        call(post("/api/cart/items"), "{\"productId\":\"GHEE\",\"feedbackId\":" + gheeFeedback + "}", 201);
        JsonNode rec = call(get("/api/recommendations/buy-again"), null, 200);
        assertThat(rec.path("items").size()).isZero();
        assertThat(rec.path("excluded").toString()).contains("SUBSCRIBED").contains("IN_CART");
    }

    // ------------------------------------------------------------------ helpers

    private long deliver(String productId) {
        CustomerOrder o = new CustomerOrder(C, time.today().minusDays(1), OrderStatus.DELIVERED, "Sector 45", "Gurugram");
        o.addItem(productId, 1, BigDecimal.TEN, PurchaseType.ONE_TIME);
        orders.save(o);
        return o.getItems().get(0).getId();
    }

    /** Rates an item via "Rate now" (no waiting); returns the feedbackId. */
    private long rate(long orderItemId, int stars) throws Exception {
        long promptId = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + orderItemId + "}", 200)
                .path("prompt").path("promptId").asLong();
        return call(post("/api/feedback-prompts/" + promptId + "/rating"), "{\"rating\":" + stars + "}", 200)
                .path("feedbackId").asLong();
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String body, int expected) throws Exception {
        req.with(jwt().jwt(j -> j.subject(String.valueOf(C))));
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult r = mvc.perform(req).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expected);
        return json.readTree(r.getResponse().getContentAsString());
    }
}
