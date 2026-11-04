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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Address book rules, and checkout delivering to the chosen / default address. */
@SpringBootTest(properties = {"app.seed.enabled=false"})
@AutoConfigureMockMvc
class AddressesTest {

    static final LocalDate T = LocalDate.of(2026, 11, 10);
    static final long C = 1L;
    static final long OTHER = 2L;

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
        products.save(new Product("PANEER", "Paneer", "200 g", new BigDecimal("95"), 1, null));
        customers.insertWithId(C, "Test", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
        customers.insertWithId(OTHER, "Other", "9000000002", "HSR Layout", "Bengaluru", null, Instant.now());
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }

    @Test
    @DisplayName("First address becomes default; makeDefault / set-default move it; delete promotes; last one stays")
    void defaultRules() throws Exception {
        long home = add("Home", "Sector 45", "122003", false).path("addressId").asLong();
        long work = add("Work", "DLF Phase 3", "122010", false).path("addressId").asLong();

        JsonNode list = call(get("/api/addresses"), null, C, 200);
        assertThat(list.path("addresses").size()).isEqualTo(2);
        assertThat(list.path("defaultAddressId").asLong()).isEqualTo(home);
        assertThat(list.path("addresses").get(0).path("fullAddress").asText())
                .isEqualTo("Flat 1, Main Road, near Metro, Sector 45, Gurugram 122003");

        JsonNode after = call(post("/api/addresses/" + work + "/default"), null, C, 200);
        assertThat(after.path("defaultAddressId").asLong()).isEqualTo(work);
        assertThat(after.path("addresses").get(0).path("addressId").asLong()).isEqualTo(work);    // default listed first

        JsonNode removed = call(delete("/api/addresses/" + work), null, C, 200);
        assertThat(removed.path("defaultAddressId").asLong()).isEqualTo(home);                     // promoted
        assertThat(call(delete("/api/addresses/" + home), null, C, 409).path("code").asText()).isEqualTo("LAST_ADDRESS");

        long third = add("Parents' home", "Sohna Road", "122018", true).path("addressId").asLong();
        assertThat(call(get("/api/addresses"), null, C, 200).path("defaultAddressId").asLong()).isEqualTo(third);
    }

    @Test
    @DisplayName("Edit, validation (pincode, required fields) and privacy")
    void editAndValidate() throws Exception {
        long id = add("Home", "Sector 45", "122003", false).path("addressId").asLong();
        JsonNode edited = call(put("/api/addresses/" + id),
                body("Home", "Flat 2", "Sector 56", "122011", false), C, 200);
        assertThat(edited.path("area").asText()).isEqualTo("Sector 56");

        call(post("/api/addresses"), body("Home", "Flat 1", "Sector 45", "12345", false), C, 400);   // bad pincode
        call(post("/api/addresses"), "{\"label\":\"Home\"}", C, 400);                               // missing fields
        call(put("/api/addresses/" + id), body("X", "Y", "Z", "122001", false), OTHER, 404);         // not theirs
        call(get("/api/addresses"), null, null, 401);                                                // login needed
    }

    @Test
    @DisplayName("Checkout delivers to the chosen address, or the default; the order keeps the address text")
    void checkoutAddress() throws Exception {
        add("Home", "Sector 45", "122003", false);
        long work = add("Work", "DLF Phase 3", "122010", false).path("addressId").asLong();

        call(post("/api/cart/items"), "{\"productId\":\"PANEER\"}", C, 201);
        JsonNode toDefault = call(post("/api/orders"), null, C, 201).path("orders").get(0);
        assertThat(toDefault.path("area").asText()).isEqualTo("Sector 45");
        assertThat(toDefault.path("deliveryAddress").asText()).contains("Sector 45, Gurugram 122003");

        call(post("/api/cart/items"), "{\"productId\":\"PANEER\"}", C, 201);
        JsonNode toWork = call(post("/api/orders"), "{\"addressId\":" + work + "}", C, 201).path("orders").get(0);
        assertThat(toWork.path("addressId").asLong()).isEqualTo(work);
        assertThat(toWork.path("area").asText()).isEqualTo("DLF Phase 3");          // quality dashboard area follows it

        call(post("/api/cart/items"), "{\"productId\":\"PANEER\"}", C, 201);
        call(post("/api/orders"), "{\"addressId\":" + work + "}", OTHER, 404);       // someone else's address
    }

    @Test
    @DisplayName("Seeded demo accounts have 4-5 addresses with exactly one default")
    void seededAddressBooks() throws Exception {
        seeder.reseed();
        for (long id : new long[]{101, 106, 202, 204}) {
            JsonNode list = call(get("/api/addresses"), null, id, 200);
            assertThat(list.path("addresses").size()).as("customer " + id).isBetween(4, 5);
            int defaults = 0;
            for (JsonNode a : list.path("addresses")) {
                defaults += a.path("isDefault").asBoolean() ? 1 : 0;
                assertThat(a.path("pincode").asText()).matches("[1-9]\\d{5}");
            }
            assertThat(defaults).isEqualTo(1);
            assertThat(list.path("addresses").get(0).path("label").asText()).isEqualTo("Home");
        }
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode add(String label, String area, String pincode, boolean makeDefault) throws Exception {
        return call(post("/api/addresses"), body(label, "Flat 1", area, pincode, makeDefault), C, 201);
    }

    private static String body(String label, String line1, String area, String pincode, boolean makeDefault) {
        return "{\"label\":\"" + label + "\",\"line1\":\"" + line1 + "\",\"line2\":\"Main Road\",\"landmark\":\"Metro\","
                + "\"area\":\"" + area + "\",\"city\":\"Gurugram\",\"pincode\":\"" + pincode + "\",\"makeDefault\":" + makeDefault + "}";
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
