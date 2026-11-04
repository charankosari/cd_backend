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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Product catalog API + product images, using the real seeded catalog. */
@SpringBootTest(properties = {"app.seed.enabled=true", "spring.datasource.url=jdbc:h2:mem:products-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ProductsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("GET /api/products: 10 products with price, pack, category, description and a full image URL")
    void listProducts() throws Exception {
        JsonNode list = json.readTree(mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString());
        assertThat(list.size()).isEqualTo(10);
        JsonNode paneer = null;
        for (JsonNode p : list) {
            assertThat(p.path("imageUrl").asText()).startsWith("http").contains("/products/");
            assertThat(p.path("description").asText()).isNotBlank();
            if (p.path("productId").asText().equals("PANEER")) {
                paneer = p;
            }
            if (p.path("productId").asText().equals("MILK")) {
                assertThat(p.path("feedbackEnabled").asBoolean()).isFalse();
            }
        }
        assertThat(paneer).isNotNull();
        assertThat(paneer.path("price").asDouble()).isEqualTo(95.0);
        assertThat(paneer.path("unit").asText()).isEqualTo("200 g");
        assertThat(paneer.path("category").asText()).isEqualTo("Dairy");
        assertThat(paneer.path("imageUrl").asText()).isEqualTo("http://localhost/products/paneer.jpg");
        assertThat(paneer.path("feedbackEnabled").asBoolean()).isTrue();
        assertThat(paneer.path("issueOptions").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("GET /api/products/{id} works in any case; unknown -> 404")
    void productDetail() throws Exception {
        JsonNode ghee = json.readTree(mvc.perform(get("/api/products/ghee")).andReturn().getResponse().getContentAsString());
        assertThat(ghee.path("productId").asText()).isEqualTo("GHEE");
        assertThat(ghee.path("feedbackDelayDays").asInt()).isEqualTo(7);
        assertThat(mvc.perform(get("/api/products/NOPE")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Every product image is served without a login, with an image content type")
    void imagesAreServed() throws Exception {
        for (String file : new String[]{"paneer.jpg", "dahi.jpg", "bread.jpg", "ghee.jpg", "atta.png", "milk.png",
                "butter.png", "eggs.png", "honey.png", "coconut.png"}) {
            MvcResult r = mvc.perform(get("/products/" + file)).andReturn();
            assertThat(r.getResponse().getStatus()).as(file).isEqualTo(200);
            assertThat(r.getResponse().getContentType()).as(file).startsWith("image/");
            assertThat(r.getResponse().getContentAsByteArray().length).as(file).isGreaterThan(1000);
        }
    }

    @Test
    @DisplayName("Behind a proxy (Railway), image URLs use the public https host")
    void imageUrlBehindProxy() throws Exception {
        JsonNode p = json.readTree(mvc.perform(get("/api/products/paneer")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "cd-feedback-backend-production.up.railway.app"))
                .andReturn().getResponse().getContentAsString());
        assertThat(p.path("imageUrl").asText())
                .isEqualTo("https://cd-feedback-backend-production.up.railway.app/products/paneer.jpg");
    }
}
