package com.countrydelight.feedback;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CartItem;
import com.countrydelight.feedback.domain.CartItemSource;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import com.countrydelight.feedback.repository.CartItemRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"app.seed.enabled=false"})
@AutoConfigureMockMvc
class CartFeaturesTest {

    static final LocalDate T = LocalDate.of(2026, 11, 10);
    static final long C = 1L;
    static final long OTHER = 2L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TimeProvider time;
    @Autowired DataSeeder seeder;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;
    @Autowired OrderRepository orders;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired CartItemRepository cartItems;

    @BeforeEach
    void setUp() {
        seeder.wipe();
        time.setToday(T);
        for (String[] p : new String[][]{{"BREAD", "Bread", "50", "2"}, {"BUTTER", "Butter", "60", "3"},
                {"EGGS", "Eggs", "72", "2"}, {"HONEY", "Honey", "199", "7"}, {"PANEER", "Paneer", "95", "1"}}) {
            products.save(new Product(p[0], p[1], "1 pc", new BigDecimal(p[2]), Integer.valueOf(p[3]), null));
        }
        customers.insertWithId(C, "Test", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
        customers.insertWithId(OTHER, "Other", "9000000002", "HSR Layout", "Bengaluru", null, Instant.now());
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }


    @Test
    @DisplayName("PATCH changes quantity and date; moving onto an existing line merges; bad input rejected")
    void updateLines() throws Exception {
        long bread = call(post("/api/cart/items"), "{\"productId\":\"BREAD\"}", C, 201).path("item").path("cartItemId").asLong();

        JsonNode qty = call(patch("/api/cart/items/" + bread), "{\"quantity\":3}", C, 200);
        assertThat(qty.path("item").path("quantity").asInt()).isEqualTo(3);

        call(post("/api/cart/items"), "{\"productId\":\"BREAD\",\"quantity\":2,\"deliveryDate\":\"" + T.plusDays(5) + "\"}", C, 201);
        JsonNode merged = call(patch("/api/cart/items/" + bread), "{\"deliveryDate\":\"" + T.plusDays(5) + "\"}", C, 200);
        assertThat(merged.path("item").path("quantity").asInt()).isEqualTo(5);
        assertThat(merged.path("cart").path("items").size()).isEqualTo(1);

        long line = merged.path("item").path("cartItemId").asLong();
        assertThat(call(patch("/api/cart/items/" + line), "{\"deliveryDate\":\"" + T + "\"}", C, 400)
                .path("code").asText()).isEqualTo("INVALID_DELIVERY_DATE");
        assertThat(call(patch("/api/cart/items/" + line), "{}", C, 400).path("code").asText()).isEqualTo("NOTHING_TO_UPDATE");
        call(patch("/api/cart/items/" + line), "{\"quantity\":21}", C, 400);
        call(patch("/api/cart/items/" + line), "{\"quantity\":1}", OTHER, 404);
    }

    @Test
    @DisplayName("Summary for the cart badge, and DELETE /api/cart empties the cart")
    void summaryAndClear() throws Exception {
        call(post("/api/cart/items"), "{\"productId\":\"BREAD\",\"quantity\":2}", C, 201);
        call(post("/api/cart/items"), "{\"productId\":\"HONEY\",\"deliveryDate\":\"" + T.plusDays(3) + "\"}", C, 201);

        JsonNode s = call(get("/api/cart/summary"), null, C, 200);
        assertThat(s.path("lineCount").asInt()).isEqualTo(2);
        assertThat(s.path("totalQuantity").asInt()).isEqualTo(3);
        assertThat(s.path("totalAmount").asDouble()).isEqualTo(299.0);
        assertThat(s.path("nextDeliveryDate").asText()).isEqualTo(T.plusDays(1).toString());

        JsonNode cleared = call(delete("/api/cart"), null, C, 200);
        assertThat(cleared.path("items").size()).isZero();
        assertThat(call(get("/api/cart/summary"), null, C, 200).path("lineCount").asInt()).isZero();
    }


    @Test
    @DisplayName("Bread in cart -> Butter, Eggs, Honey, for the bread's delivery date; empty cart -> nothing")
    void suggestionsFromCart() throws Exception {
        assertThat(call(get("/api/recommendations/for-cart"), null, C, 200).path("items").size()).isZero();

        call(post("/api/cart/items"), "{\"productId\":\"BREAD\",\"deliveryDate\":\"" + T.plusDays(2) + "\"}", C, 201);
        JsonNode rec = call(get("/api/recommendations/for-cart"), null, C, 200);
        assertThat(ids(rec.path("items"))).containsExactly("BUTTER", "EGGS", "HONEY");
        assertThat(rec.path("items").get(0).path("reason").asText()).isEqualTo("Goes well with Bread");
        assertThat(rec.path("items").get(0).path("suggestedDate").asText()).isEqualTo(T.plusDays(2).toString());
    }

    @Test
    @DisplayName("Suggestions leave out what's in the cart, low-rated and subscribed products; liked ones come first")
    void suggestionRules() throws Exception {
        call(post("/api/cart/items"), "{\"productId\":\"BREAD\"}", C, 201);
        call(post("/api/cart/items"), "{\"productId\":\"BUTTER\"}", C, 201);
        rate(deliver("EGGS"), 2);
        rate(deliver("HONEY"), 5);

        JsonNode rec = call(get("/api/recommendations/for-cart"), null, C, 200);
        assertThat(ids(rec.path("items")).get(0)).isEqualTo("HONEY");
        assertThat(rec.path("items").get(0).path("yourRating").asInt()).isEqualTo(5);
        assertThat(rec.path("items").get(0).path("reason").asText()).contains("you rated it 5");
        assertThat(rec.path("excluded").toString()).contains("\"EGGS\"").contains("RATED_LOW").contains("IN_CART");

        subscriptions.save(new Subscription(C, "HONEY", SubscriptionStatus.ACTIVE, T.minusDays(1), null));
        JsonNode after = call(get("/api/recommendations/for-cart"), null, C, 200);
        assertThat(ids(after.path("items"))).doesNotContain("HONEY");
        assertThat(after.path("excluded").toString()).contains("SUBSCRIBED");
    }


    @Test
    @DisplayName("Dashboard carts: live carts grouped by customer; lines whose date has passed are not shown")
    void dashboardCarts() throws Exception {
        call(post("/api/cart/items"), "{\"productId\":\"BREAD\",\"quantity\":2}", C, 201);
        call(post("/api/cart/items"), "{\"productId\":\"HONEY\"}", OTHER, 201);
        cartItems.save(new CartItem(C, "PANEER", 1, T.minusDays(1), CartItemSource.MANUAL, null, Instant.now()));

        JsonNode d = call(get("/api/dashboard/carts"), null, null, 200);
        assertThat(d.path("customers").asInt()).isEqualTo(2);
        assertThat(d.path("lines").asInt()).isEqualTo(2);
        assertThat(d.path("totalValue").asDouble()).isEqualTo(299.0);
        JsonNode mine = null;
        for (JsonNode c : d.path("carts")) {
            if (c.path("customerId").asLong() == C) {
                mine = c;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.path("customerName").asText()).isEqualTo("Test");
        assertThat(mine.path("items").size()).isEqualTo(1);
        assertThat(mine.path("items").get(0).path("productId").asText()).isEqualTo("BREAD");
    }


    private long deliver(String productId) {
        CustomerOrder o = new CustomerOrder(C, T.minusDays(1), OrderStatus.DELIVERED, "Sector 45", "Gurugram");
        o.addItem(productId, 1, BigDecimal.TEN, PurchaseType.ONE_TIME);
        orders.save(o);
        return o.getItems().get(0).getId();
    }

    private void rate(long orderItemId, int stars) throws Exception {
        long promptId = call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + orderItemId + "}", C, 200)
                .path("prompt").path("promptId").asLong();
        call(post("/api/feedback-prompts/" + promptId + "/rating"), "{\"rating\":" + stars + "}", C, 200);
    }

    private List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path("productId").asText()));
        return out;
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String body, Long customerId, int expected) throws Exception {
        if (customerId != null) {
            req.with(jwt().jwt(j -> j.subject(String.valueOf(customerId))));
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult r = mvc.perform(req).andReturn();
        String content = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getResponse().getStatus()).as(content).isEqualTo(expected);
        return content.isEmpty() ? json.createObjectNode() : json.readTree(content);
    }
}
