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
class DashboardOrdersTest {

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
        customers.insertWithId(C, "Test Customer", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }

    @Test
    @DisplayName("A placed order shows as SCHEDULED; Mark delivered delivers it today and Rate now works at once")
    void markDelivered() throws Exception {
        call(post("/api/cart/items"), "{\"productId\":\"PANEER\",\"quantity\":2}", true, 201);
        long orderId = call(post("/api/orders"), null, true, 201).path("orders").get(0).path("orderId").asLong();

        JsonNode scheduled = call(get("/api/dashboard/orders?status=SCHEDULED"), null, false, 200);
        assertThat(scheduled.path("counts").path("SCHEDULED").asLong()).isEqualTo(1);
        JsonNode row = scheduled.path("content").get(0);
        assertThat(row.path("orderId").asLong()).isEqualTo(orderId);
        assertThat(row.path("customerName").asText()).isEqualTo("Test Customer");
        assertThat(row.path("deliveryDate").asText()).isEqualTo(T.plusDays(1).toString());
        assertThat(row.path("canMarkDelivered").asBoolean()).isTrue();
        assertThat(row.path("totalAmount").asDouble()).isEqualTo(190.0);

        JsonNode delivered = call(post("/api/dashboard/orders/" + orderId + "/deliver"), null, false, 200);
        assertThat(delivered.path("order").path("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.path("order").path("deliveryDate").asText()).isEqualTo(T.toString());
        assertThat(delivered.path("order").path("canMarkDelivered").asBoolean()).isFalse();
        assertThat(delivered.path("message").asText()).contains("marked delivered");

        assertThat(call(post("/api/dashboard/orders/" + orderId + "/deliver"), null, false, 409)
                .path("code").asText()).isEqualTo("ALREADY_DELIVERED");
        call(post("/api/dashboard/orders/999999/deliver"), null, false, 404);

        JsonNode myOrders = call(get("/api/orders"), null, true, 200);
        JsonNode item = myOrders.get(0).path("items").get(0);
        assertThat(item.path("feedback").path("canRateNow").asBoolean()).isTrue();
        call(post("/api/feedback-prompts/rate-now"), "{\"orderItemId\":" + item.path("orderItemId").asLong() + "}", true, 200);
    }

    @Test
    @DisplayName("Orders whose delivery day has come show as DELIVERED; status filter and bad status")
    void autoDeliveredAndFilters() throws Exception {
        CustomerOrder due = new CustomerOrder(C, T, OrderStatus.SCHEDULED, "Sector 45", "Gurugram");
        due.addItem("PANEER", 1, BigDecimal.TEN, PurchaseType.ONE_TIME);
        orders.save(due);

        JsonNode all = call(get("/api/dashboard/orders"), null, false, 200);
        assertThat(all.path("content").get(0).path("status").asText()).isEqualTo("DELIVERED");
        assertThat(all.path("counts").path("ALL").asLong()).isEqualTo(1);
        assertThat(call(get("/api/dashboard/orders?status=SCHEDULED"), null, false, 200)
                .path("totalElements").asLong()).isZero();
        assertThat(call(get("/api/dashboard/orders?status=LOST"), null, false, 400).path("code").asText())
                .isEqualTo("BAD_STATUS");
    }

    @Test
    @DisplayName("Orders are newest first and can be filtered by one-time / subscription")
    void newestFirstAndTypeFilter() throws Exception {
        CustomerOrder older = new CustomerOrder(C, T.plusDays(1), OrderStatus.SCHEDULED, "Sector 45", "Gurugram");
        older.addItem("PANEER", 1, BigDecimal.TEN, PurchaseType.SUBSCRIPTION);
        older.setPlacedAt(Instant.parse("2026-11-09T10:00:00Z"));
        orders.save(older);
        CustomerOrder newer = new CustomerOrder(C, T.plusDays(1), OrderStatus.SCHEDULED, "Sector 45", "Gurugram");
        newer.addItem("PANEER", 2, BigDecimal.TEN, PurchaseType.ONE_TIME);
        newer.setPlacedAt(Instant.parse("2026-11-09T12:00:00Z"));
        orders.save(newer);

        JsonNode all = call(get("/api/dashboard/orders"), null, false, 200);
        assertThat(all.path("content").get(0).path("orderId").asLong()).isEqualTo(newer.getId());
        assertThat(all.path("content").get(1).path("orderId").asLong()).isEqualTo(older.getId());
        assertThat(all.path("counts").path("ONE_TIME").asLong()).isEqualTo(1);
        assertThat(all.path("counts").path("SUBSCRIPTION").asLong()).isEqualTo(1);

        JsonNode subs = call(get("/api/dashboard/orders?type=SUBSCRIPTION"), null, false, 200);
        assertThat(subs.path("totalElements").asLong()).isEqualTo(1);
        assertThat(subs.path("content").get(0).path("orderType").asText()).isEqualTo("SUBSCRIPTION");
        assertThat(call(get("/api/dashboard/orders?type=WEEKLY"), null, false, 400).path("code").asText())
                .isEqualTo("BAD_TYPE");

        assertThat(call(get("/api/orders"), null, true, 200).get(0).path("orderId").asLong()).isEqualTo(newer.getId());
    }

    @Test
    @DisplayName("Dashboard subscriptions: newest first, status filter and counts")
    void subscriptionsFilter() throws Exception {
        products.save(new Product("DAHI", "Dahi", "400 g", new BigDecimal("50"), 1, null));
        long first = call(post("/api/subscriptions"), "{\"productId\":\"PANEER\",\"quantity\":1}", true, 201)
                .path("subscriptionId").asLong();
        long second = call(post("/api/subscriptions"), "{\"productId\":\"DAHI\",\"quantity\":2}", true, 201)
                .path("subscriptionId").asLong();
        call(post("/api/subscriptions/" + first + "/pause"), null, true, 200);

        JsonNode all = call(get("/api/dashboard/subscriptions"), null, false, 200);
        assertThat(all.path("content").get(0).path("subscriptionId").asLong()).isEqualTo(second);
        assertThat(all.path("content").get(0).path("customerName").asText()).isEqualTo("Test Customer");
        assertThat(all.path("counts").path("ACTIVE").asLong()).isEqualTo(1);
        assertThat(all.path("counts").path("PAUSED").asLong()).isEqualTo(1);
        assertThat(all.path("counts").path("ALL").asLong()).isEqualTo(2);

        JsonNode paused = call(get("/api/dashboard/subscriptions?status=PAUSED"), null, false, 200);
        assertThat(paused.path("content")).hasSize(1);
        assertThat(paused.path("content").get(0).path("productId").asText()).isEqualTo("PANEER");
        assertThat(call(get("/api/dashboard/subscriptions?productId=dahi"), null, false, 200).path("content")).hasSize(1);
        assertThat(call(get("/api/dashboard/subscriptions?status=GONE"), null, false, 400).path("code").asText())
                .isEqualTo("BAD_STATUS");
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String body, boolean asCustomer, int expected) throws Exception {
        if (asCustomer) {
            req.with(jwt().jwt(j -> j.subject(String.valueOf(C))));
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult r = mvc.perform(req).andReturn();
        String content = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getResponse().getStatus()).as(content).isEqualTo(expected);
        return json.readTree(content);
    }
}
