package com.countrydelight.feedback;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.IssueOption;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.IssueOptionRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Orders API (checkout from cart, my orders, delivery) and "Rate now" (skip the X-day wait). */
@SpringBootTest(properties = {"app.seed.enabled=false"})
@AutoConfigureMockMvc
class OrdersAndRateNowTest {

    static final LocalDate T = LocalDate.of(2026, 11, 10);
    static final long C = 1L;
    static final long OTHER = 2L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TimeProvider time;
    @Autowired DataSeeder seeder;
    @Autowired ProductRepository products;
    @Autowired IssueOptionRepository issueOptions;
    @Autowired CustomerRepository customers;
    @Autowired OrderRepository orders;

    @BeforeEach
    void setUp() {
        seeder.wipe();
        time.setToday(T);
        products.save(new Product("PANEER", "Paneer", "200 g", new BigDecimal("95"), 1, null));
        products.save(new Product("GHEE", "Ghee", "500 ml", new BigDecimal("375"), 7, null));
        products.save(new Product("MILK", "Milk", "500 ml", new BigDecimal("34"), null, null));
        issueOptions.save(new IssueOption("PANEER", "HARD", "Too hard", 1));
        issueOptions.save(new IssueOption("GHEE", "RANCID", "Off taste", 1));
        customers.insertWithId(C, "Test", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
        customers.insertWithId(OTHER, "Other", "9000000002", "HSR Layout", "Bengaluru", null, Instant.now());
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }

    // ------------------------------------------------------------------ orders

    @Test
    @DisplayName("Checkout: cart becomes one order per date (SCHEDULED); delivered on its date; then the auto card appears")
    void checkoutDeliveryAndAutoCard() throws Exception {
        call(post("/api/cart/items"), "{\"productId\":\"PANEER\",\"quantity\":2}", C, 201);                       // T+1
        call(post("/api/cart/items"), "{\"productId\":\"GHEE\",\"deliveryDate\":\"" + T.plusDays(3) + "\"}", C, 201);

        JsonNode placed = call(post("/api/orders"), null, C, 201);
        assertThat(placed.path("orders").size()).isEqualTo(2);
        assertThat(placed.path("message").asText()).isEqualTo("2 orders placed");
        assertThat(placed.path("orders").get(0).path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(call(get("/api/cart"), null, C, 200).path("items").size()).isZero();

        JsonNode list = call(get("/api/orders"), null, C, 200);
        assertThat(list.get(0).path("deliveryDate").asText()).isEqualTo(T.plusDays(3).toString());   // newest first
        JsonNode paneerItem = list.get(1).path("items").get(0);
        assertThat(paneerItem.path("quantity").asInt()).isEqualTo(2);
        assertThat(paneerItem.path("lineTotal").asDouble()).isEqualTo(190.0);
        assertThat(paneerItem.path("feedback").path("status").asText()).isEqualTo("NOT_DELIVERED");
        assertThat(paneerItem.path("feedback").path("canRateNow").asBoolean()).isFalse();
        assertThat(paneerItem.path("feedback").path("autoAskOn").asText()).isEqualTo(T.plusDays(2).toString());

        time.setToday(T.plusDays(1));                                   // paneer delivery day
        JsonNode afterDelivery = call(get("/api/orders"), null, C, 200);
        assertThat(afterDelivery.get(1).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(afterDelivery.get(1).path("items").get(0).path("feedback").path("status").asText()).isEqualTo("NOT_RATED");
        assertThat(afterDelivery.get(1).path("items").get(0).path("feedback").path("canRateNow").asBoolean()).isTrue();
        assertThat(afterDelivery.get(0).path("status").asText()).isEqualTo("SCHEDULED");   // ghee not yet

        time.setToday(T.plusDays(2));                                   // delivery + X(1)
        JsonNode card = call(get("/api/feedback-prompts/next"), null, C, 200);
        assertThat(card.path("show").asBoolean()).isTrue();
        assertThat(card.path("prompt").path("productId").asText()).isEqualTo("PANEER");
    }

    @Test
    @DisplayName("Checkout errors: empty cart, a cart date that has passed; deliveryDate places only that day")
    void checkoutRules() throws Exception {
        assertThat(call(post("/api/orders"), null, C, 400).path("code").asText()).isEqualTo("CART_EMPTY");

        call(post("/api/cart/items"), "{\"productId\":\"PANEER\"}", C, 201);                                      // T+1
        call(post("/api/cart/items"), "{\"productId\":\"GHEE\",\"deliveryDate\":\"" + T.plusDays(5) + "\"}", C, 201);
        JsonNode one = call(post("/api/orders"), "{\"deliveryDate\":\"" + T.plusDays(5) + "\"}", C, 201);
        assertThat(one.path("orders").size()).isEqualTo(1);
        assertThat(one.path("orders").get(0).path("items").get(0).path("productId").asText()).isEqualTo("GHEE");
        assertThat(call(get("/api/cart"), null, C, 200).path("items").size()).isEqualTo(1);    // paneer still in cart

        time.setToday(T.plusDays(1));                                   // paneer's cart date is now today
        assertThat(call(post("/api/orders"), null, C, 400).path("code").asText()).isEqualTo("CART_DATE_PASSED");
    }

    @Test
    @DisplayName("Orders are private: another customer's order -> 404")
    void ordersArePrivate() throws Exception {
        CustomerOrder mine = deliveredOrder(1, "PANEER");
        call(get("/api/orders/" + mine.getId()), null, C, 200);
        call(get("/api/orders/" + mine.getId()), null, OTHER, 404);
        assertThat(call(get("/api/orders"), null, OTHER, 200).size()).isZero();
    }

    // ------------------------------------------------------------------ rate now

    @Test
    @DisplayName("Rate now: skips the 7-day wait for ghee; same card on repeat; rated item shows RATED; can't rate twice")
    void rateNowSkipsTheWait() throws Exception {
        long itemId = deliveredOrder(1, "GHEE").getItems().get(0).getId();
        assertThat(call(get("/api/feedback-prompts/next"), null, C, 200).path("show").asBoolean()).isFalse();  // not due

        JsonNode card = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + itemId + "}", C, 200);
        assertThat(card.path("show").asBoolean()).isTrue();
        assertThat(card.path("prompt").path("productId").asText()).isEqualTo("GHEE");
        assertThat(card.path("prompt").path("reasons").toString()).contains("USER_REQUESTED");
        long promptId = card.path("prompt").path("promptId").asLong();

        JsonNode again = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + itemId + "}", C, 200);
        assertThat(again.path("prompt").path("promptId").asLong()).isEqualTo(promptId);

        JsonNode rated = call(post("/api/feedback-prompts/" + promptId + "/rating"), "{\"rating\":5}", C, 200);
        assertThat(rated.path("nextStep").asText()).isEqualTo("BUY_AGAIN");

        JsonNode item = call(get("/api/orders"), null, C, 200).get(0).path("items").get(0).path("feedback");
        assertThat(item.path("status").asText()).isEqualTo("RATED");
        assertThat(item.path("rating").asInt()).isEqualTo(5);
        assertThat(item.path("canRateNow").asBoolean()).isFalse();

        JsonNode twice = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + itemId + "}", C, 409);
        assertThat(twice.path("code").asText()).isEqualTo("ALREADY_RATED");
    }

    @Test
    @DisplayName("Rate now doesn't use up the frequency cap, and an abandoned one isn't pushed to the home screen")
    void rateNowIsSeparateFromHomeCard() throws Exception {
        long gheeItem = deliveredOrder(1, "GHEE").getItems().get(0).getId();
        deliveredOrder(1, "PANEER");                                    // due today (X=1)

        long gheePrompt = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + gheeItem + "}", C, 200)
                .path("prompt").path("promptId").asLong();
        JsonNode home = call(get("/api/feedback-prompts/next"), null, C, 200);
        assertThat(home.path("prompt").path("productId").asText()).isEqualTo("PANEER");    // not the abandoned ghee

        call(post("/api/feedback-prompts/" + gheePrompt + "/rating"), "{\"rating\":2}", C, 200);
        JsonNode stillHome = call(get("/api/feedback-prompts/next"), null, C, 200);
        assertThat(stillHome.path("show").asBoolean()).isTrue();                            // no cap from rate-now
        assertThat(stillHome.path("prompt").path("productId").asText()).isEqualTo("PANEER");
    }

    @Test
    @DisplayName("A skipped home card can still be rated later with Rate now")
    void skippedThenRateNow() throws Exception {
        long itemId = deliveredOrder(1, "PANEER").getItems().get(0).getId();
        long promptId = call(get("/api/feedback-prompts/next"), null, C, 200).path("prompt").path("promptId").asLong();
        call(post("/api/feedback-prompts/" + promptId + "/skip"), null, C, 200);

        JsonNode reopened = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + itemId + "}", C, 200);
        assertThat(reopened.path("prompt").path("promptId").asLong()).isEqualTo(promptId);
        call(post("/api/feedback-prompts/" + promptId + "/rating"), "{\"rating\":3}", C, 200);
    }

    @Test
    @DisplayName("Rate now errors: not delivered yet (409), not your item (404), product without feedback (400)")
    void rateNowErrors() throws Exception {
        call(post("/api/cart/items"), "{\"productId\":\"PANEER\"}", C, 201);
        long scheduledItem = call(post("/api/orders"), null, C, 201)
                .path("orders").get(0).path("items").get(0).path("orderItemId").asLong();
        assertThat(call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + scheduledItem + "}", C, 409)
                .path("code").asText()).isEqualTo("NOT_DELIVERED");

        long paneer = deliveredOrder(1, "PANEER").getItems().get(0).getId();
        call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + paneer + "}", OTHER, 404);

        long milk = deliveredOrder(1, "MILK").getItems().get(0).getId();
        assertThat(call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + milk + "}", C, 400)
                .path("code").asText()).isEqualTo("FEEDBACK_NOT_AVAILABLE");
    }

    // ------------------------------------------------------------------ helpers

    private CustomerOrder deliveredOrder(int daysAgo, String productId) {
        CustomerOrder o = new CustomerOrder(C, T.minusDays(daysAgo), OrderStatus.DELIVERED, "Sector 45", "Gurugram");
        o.addItem(productId, 1, BigDecimal.TEN, PurchaseType.ONE_TIME);
        orders.save(o);
        return o;
    }

    private static RequestPostProcessor as(long customerId) {
        return jwt().jwt(j -> j.subject(String.valueOf(customerId)));
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String body, long customerId, int expected) throws Exception {
        req.with(as(customerId));
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult r = mvc.perform(req).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expected);
        String content = r.getResponse().getContentAsString();
        return content.isEmpty() ? json.createObjectNode() : json.readTree(content);
    }
}
