package com.countrydelight.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {"app.seed.enabled=true", "spring.datasource.url=jdbc:h2:mem:demo-accounts;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class DemoAccountsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("201 Ravi (subscriber): no feedback card; paneer not recommended because he's subscribed")
    void subscriber() throws Exception {
        JsonNode next = call("/api/feedback-prompts/next", 201);
        assertThat(next.path("show").asBoolean()).isFalse();

        JsonNode rec = call("/api/recommendations/buy-again", 201);
        assertThat(rec.path("items").size()).isZero();
        assertThat(rec.path("excluded").get(0).path("productId").asText()).isEqualTo("PANEER");
        assertThat(rec.path("excluded").get(0).path("reason").asText()).isEqualTo("SUBSCRIBED");
    }

    @Test
    @DisplayName("202 Anjali (happy): ghee 5★ and paneer 4★ recommended; dahi card shows today")
    void happyCustomer() throws Exception {
        JsonNode rec = call("/api/recommendations/buy-again", 202);
        assertThat(ids(rec.path("items"))).containsExactly("GHEE", "PANEER");
        assertThat(rec.path("items").get(0).path("reason").asText()).isEqualTo("You rated it 5★");
        assertThat(rec.path("items").get(0).path("feedbackId").asLong()).isPositive();
        assertThat(rec.path("excluded").size()).isZero();

        JsonNode next = call("/api/feedback-prompts/next", 202);
        assertThat(next.path("prompt").path("productId").asText()).isEqualTo("DAHI");
    }

    @Test
    @DisplayName("203 Karan (mixed): only eggs 5★ recommended; bread 2★ and atta 3★ left out; coconut water card today")
    void mixedRatings() throws Exception {
        JsonNode rec = call("/api/recommendations/buy-again", 203);
        assertThat(ids(rec.path("items"))).containsExactly("EGGS");
        assertThat(ids(rec.path("excluded"))).containsExactlyInAnyOrder("BREAD", "ATTA");
        rec.path("excluded").forEach(e -> assertThat(e.path("reason").asText()).isEqualTo("RATED_LOW"));

        JsonNode next = call("/api/feedback-prompts/next", 203);
        assertThat(next.path("prompt").path("productId").asText()).isEqualTo("COCONUT_WATER");
    }

    @Test
    @DisplayName("204 Pooja (subscribed to Dahi, ordered Paneer): asked about Paneer only; Dahi is SUBSCRIBED")
    void subscribedPlusOrdered() throws Exception {
        JsonNode next = call("/api/feedback-prompts/next", 204);
        assertThat(next.path("show").asBoolean()).isTrue();
        assertThat(next.path("prompt").path("productId").asText()).isEqualTo("PANEER");

        MvcResult r = mvc.perform(get("/api/admin/customers/204/eligibility")).andReturn();
        JsonNode report = json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        List<String> dahiVerdicts = new ArrayList<>();
        report.path("deliveredItems").forEach(i -> {
            if (i.path("productId").asText().equals("DAHI")) {
                dahiVerdicts.add(i.path("verdict").asText());
            }
        });
        assertThat(dahiVerdicts).isNotEmpty().doesNotContain("ELIGIBLE").contains("SUBSCRIBED");
    }

    @Test
    @DisplayName("Seeded subscriptions: Ravi has 3 active plans and today's subscription delivery exists")
    void seededSubscriptions() throws Exception {
        JsonNode subs = call("/api/subscriptions", 201);
        assertThat(subs.path("activeCount").asInt()).isEqualTo(3);
        JsonNode orders = call("/api/orders", 201);
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        boolean todaysDelivery = false;
        for (JsonNode o : orders) {
            if (o.path("deliveryDate").asText().equals(today)) {
                todaysDelivery |= o.path("items").get(0).path("purchaseType").asText().equals("SUBSCRIPTION");
            }
        }
        assertThat(todaysDelivery).isTrue();
    }

    private List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path("productId").asText()));
        return out;
    }

    private JsonNode call(String path, long customerId) throws Exception {
        MvcResult r = mvc.perform(get(path).with(jwt().jwt(j -> j.subject(String.valueOf(customerId))))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
