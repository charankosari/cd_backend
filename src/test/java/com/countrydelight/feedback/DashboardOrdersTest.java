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

/** Dashboard orders screen + "Mark delivered". */
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
        call(post("/api/cart/items"), "{\"productId\":\"PANEER\",\"quantity\":2}", true, 201);     // for T+1
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
        assertThat(delivered.path("order").path("deliveryDate").asText()).isEqualTo(T.toString());   // moved to today
        assertThat(delivered.path("order").path("canMarkDelivered").asBoolean()).isFalse();
        assertThat(delivered.path("message").asText()).contains("marked delivered");

        assertThat(call(post("/api/dashboard/orders/" + orderId + "/deliver"), null, false, 409)
                .path("code").asText()).isEqualTo("ALREADY_DELIVERED");
        call(post("/api/dashboard/orders/999999/deliver"), null, false, 404);

        // The customer can rate it straight away.
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
