package com.countrydelight.feedback;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.OrderRepository;
import com.countrydelight.feedback.repository.ProductRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"app.seed.enabled=false"})
@AutoConfigureMockMvc
class OneReviewPerProductTest {

    static final LocalDate T = LocalDate.of(2026, 11, 10);
    static final long C = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TimeProvider time;
    @Autowired DataSeeder seeder;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;
    @Autowired OrderRepository orders;

    @BeforeEach
    void setUp() {
        seeder.wipe();
        time.setToday(T);
        products.save(new Product("PANEER", "Paneer", "200 g", new BigDecimal("95"), 1, null));
        products.save(new Product("DAHI", "Dahi", "400 g", new BigDecimal("45"), 1, null));
        customers.insertWithId(C, "Test", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }

    @Test
    @DisplayName("Rated paneer, ordered it again 2 months later: no card, no Rate now; order shows the earlier rating")
    void reorderAfterReviewIsNeverAsked() throws Exception {
        deliver(1, "PANEER");
        long promptId = call(get("/api/feedback-prompts/next"), null, 200).path("prompt").path("promptId").asLong();
        call(post("/api/feedback-prompts/" + promptId + "/rating"), "{\"rating\":4}", 200);

        time.setToday(T.plusDays(60));
        long again = deliver(1, "PANEER");
        JsonNode next = call(get("/api/feedback-prompts/next"), null, 200);
        assertThat(next.path("show").asBoolean()).isFalse();

        JsonNode item = call(get("/api/orders"), null, 200).get(0).path("items").get(0);
        assertThat(item.path("feedback").path("status").asText()).isEqualTo("ALREADY_REVIEWED");
        assertThat(item.path("feedback").path("canRateNow").asBoolean()).isFalse();
        assertThat(item.path("feedback").path("rating").asInt()).isEqualTo(4);

        JsonNode refused = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + again + "}", 409);
        assertThat(refused.path("code").asText()).isEqualTo("ALREADY_REVIEWED");
        assertThat(refused.path("message").asText()).contains("Paneer");
    }

    @Test
    @DisplayName("Reviewed via Rate now: the waiting home card for the same product is withdrawn and can't be rated")
    void rateNowWithdrawsTheHomeCard() throws Exception {
        deliver(1, "PANEER");
        long other = deliver(2, "PANEER");
        long homeCard = call(get("/api/feedback-prompts/next"), null, 200).path("prompt").path("promptId").asLong();

        long rn = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + other + "}", 200)
                .path("prompt").path("promptId").asLong();
        assertThat(rn).isNotEqualTo(homeCard);
        call(post("/api/feedback-prompts/" + rn + "/rating"), "{\"rating\":5}", 200);

        JsonNode secondReview = call(post("/api/feedback-prompts/" + homeCard + "/rating"), "{\"rating\":2}", 409);
        assertThat(secondReview.path("code").asText()).isEqualTo("ALREADY_REVIEWED");
        assertThat(call(get("/api/feedback-prompts/next"), null, 200).path("show").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Only a real review counts: after a skip, a reorder can still be rated; other products unaffected")
    void skipIsNotAReview() throws Exception {
        deliver(1, "PANEER");
        long promptId = call(get("/api/feedback-prompts/next"), null, 200).path("prompt").path("promptId").asLong();
        call(post("/api/feedback-prompts/" + promptId + "/skip"), null, 200);

        long again = deliver(1, "PANEER");
        call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + again + "}", 200);

        long dahi = deliver(1, "DAHI");
        call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + dahi + "}", 200);
    }


    private long deliver(int daysAgo, String productId) {
        CustomerOrder o = new CustomerOrder(C, time.today().minusDays(daysAgo), OrderStatus.DELIVERED, "Sector 45", "Gurugram");
        o.addItem(productId, 1, BigDecimal.TEN, PurchaseType.ONE_TIME);
        orders.save(o);
        return o.getItems().get(0).getId();
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String body, int expected) throws Exception {
        req.with(jwt().jwt(j -> j.subject(String.valueOf(C))));
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult r = mvc.perform(req).andReturn();
        String content = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getResponse().getStatus()).as(content).isEqualTo(expected);
        return content.isEmpty() ? json.createObjectNode() : json.readTree(content);
    }
}
