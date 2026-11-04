package com.countrydelight.feedback;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.repository.CustomerRepository;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Subscriptions: subscribe, deliveries on schedule, pause/resume, change, cancel; never asked for feedback. */
@SpringBootTest(properties = {"app.seed.enabled=false"})
@AutoConfigureMockMvc
class SubscriptionsTest {

    static final LocalDate T = LocalDate.of(2026, 11, 10);
    static final long C = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TimeProvider time;
    @Autowired DataSeeder seeder;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;

    @BeforeEach
    void setUp() {
        seeder.wipe();
        time.setToday(T);
        products.save(new Product("MILK", "Milk", "500 ml", new BigDecimal("34"), null, null));
        products.save(new Product("PANEER", "Paneer", "200 g", new BigDecimal("95"), 1, null));
        Product atta = new Product("ATTA", "Atta", "5 kg", new BigDecimal("285"), 5, null);
        atta.setSubscribable(false);
        products.save(atta);
        customers.insertWithId(C, "Test", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
        call(post("/api/addresses"), "{\"label\":\"Home\",\"line1\":\"Flat 1\",\"area\":\"Sector 45\",\"city\":\"Gurugram\",\"pincode\":\"122003\"}", 201);
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }

    @Test
    @DisplayName("Subscribe: defaults to tomorrow + default address; monthly estimate; rules for duplicates / atta / dates")
    void subscribeRules() throws Exception {
        JsonNode s = call(post("/api/subscriptions"), "{\"productId\":\"MILK\",\"quantity\":2}", 201);
        assertThat(s.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(s.path("frequency").asText()).isEqualTo("DAILY");
        assertThat(s.path("startDate").asText()).isEqualTo(T.plusDays(1).toString());
        assertThat(s.path("nextDeliveryDate").asText()).isEqualTo(T.plusDays(1).toString());
        assertThat(s.path("estimatedMonthlyCost").asDouble()).isEqualTo(34 * 2 * 30.0);
        assertThat(s.path("deliveryAddress").asText()).contains("Sector 45, Gurugram 122003");

        assertThat(call(post("/api/subscriptions"), "{\"productId\":\"MILK\"}", 409).path("code").asText()).isEqualTo("ALREADY_SUBSCRIBED");
        assertThat(call(post("/api/subscriptions"), "{\"productId\":\"ATTA\"}", 400).path("code").asText()).isEqualTo("NOT_SUBSCRIBABLE");
        assertThat(call(post("/api/subscriptions"), "{\"productId\":\"PANEER\",\"startDate\":\"" + T + "\"}", 400)
                .path("code").asText()).isEqualTo("INVALID_START_DATE");
        call(post("/api/subscriptions"), "{\"productId\":\"PANEER\",\"frequency\":\"WEEKLY\"}", 400);

        JsonNode list = call(get("/api/subscriptions"), null, 200);
        assertThat(list.path("activeCount").asInt()).isEqualTo(1);
        assertThat(list.path("estimatedMonthlyTotal").asDouble()).isEqualTo(2040.0);
    }

    @Test
    @DisplayName("Deliveries appear as orders on schedule (daily + alternate days), and are never asked for feedback")
    void deliveriesOnSchedule() throws Exception {
        call(post("/api/subscriptions"), "{\"productId\":\"MILK\",\"quantity\":2}", 201);
        call(post("/api/subscriptions"), "{\"productId\":\"PANEER\",\"frequency\":\"ALTERNATE_DAYS\"}", 201);

        time.setToday(T.plusDays(4));
        JsonNode orders = call(get("/api/orders"), null, 200);
        assertThat(orders.size()).isEqualTo(4);                                   // T+1 .. T+4, one per morning
        List<String> paneerDays = new ArrayList<>();
        for (JsonNode o : orders) {
            assertThat(o.path("status").asText()).isEqualTo("DELIVERED");
            for (JsonNode i : o.path("items")) {
                assertThat(i.path("purchaseType").asText()).isEqualTo("SUBSCRIPTION");
                if (i.path("productId").asText().equals("MILK")) {
                    assertThat(i.path("quantity").asInt()).isEqualTo(2);
                } else {
                    paneerDays.add(o.path("deliveryDate").asText());
                }
            }
        }
        assertThat(paneerDays).containsExactlyInAnyOrder(T.plusDays(1).toString(), T.plusDays(3).toString());
        assertThat(call(get("/api/orders"), null, 200).size()).isEqualTo(4);   // no duplicates on re-read

        JsonNode card = call(get("/api/feedback-prompts/next"), null, 200);
        assertThat(card.path("show").asBoolean()).isFalse();                        // subscribed paneer never asked
    }

    @Test
    @DisplayName("Pause stops deliveries; resume continues tomorrow without back-filling; change applies to next delivery")
    void pauseResumeUpdate() throws Exception {
        long id = call(post("/api/subscriptions"), "{\"productId\":\"MILK\"}", 201).path("subscriptionId").asLong();
        time.setToday(T.plusDays(2));
        JsonNode paused = call(post("/api/subscriptions/" + id + "/pause"), null, 200);
        assertThat(paused.path("status").asText()).isEqualTo("PAUSED");
        assertThat(paused.has("nextDeliveryDate")).isFalse();

        time.setToday(T.plusDays(5));
        assertThat(call(get("/api/orders"), null, 200).size()).isEqualTo(2);       // T+1, T+2 only
        JsonNode resumed = call(post("/api/subscriptions/" + id + "/resume"), null, 200);
        assertThat(resumed.path("nextDeliveryDate").asText()).isEqualTo(T.plusDays(6).toString());

        call(patch("/api/subscriptions/" + id), "{\"quantity\":3}", 200);
        time.setToday(T.plusDays(6));
        JsonNode orders = call(get("/api/orders"), null, 200);
        assertThat(orders.size()).isEqualTo(3);
        assertThat(orders.get(0).path("items").get(0).path("quantity").asInt()).isEqualTo(3);   // newest first
        call(patch("/api/subscriptions/" + id), "{}", 400);
    }

    @Test
    @DisplayName("Cancel: kept as CANCELLED, can't be changed, product can be subscribed again")
    void cancel() throws Exception {
        long id = call(post("/api/subscriptions"), "{\"productId\":\"MILK\"}", 201).path("subscriptionId").asLong();
        JsonNode cancelled = call(delete("/api/subscriptions/" + id), null, 200);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("endDate").asText()).isEqualTo(T.toString());
        assertThat(call(post("/api/subscriptions/" + id + "/pause"), null, 409).path("code").asText())
                .isEqualTo("SUBSCRIPTION_CANCELLED");
        call(post("/api/subscriptions"), "{\"productId\":\"MILK\"}", 201);
        call(delete("/api/subscriptions/999999"), null, 404);
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String body, int expected) {
        try {
            req.with(jwt().jwt(j -> j.subject(String.valueOf(C))));
            if (body != null) {
                req.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            MvcResult r = mvc.perform(req).andReturn();
            String content = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(r.getResponse().getStatus()).as(content).isEqualTo(expected);
            return content.isEmpty() ? json.createObjectNode() : json.readTree(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
