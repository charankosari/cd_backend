package com.countrydelight.feedback;

import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.seed.DataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real login flow, no shortcuts: request OTP, login or register, then use the returned token.
 * The resend cooldown is 0 here so tests can request several codes; OtpCooldownTest covers the cooldown.
 */
@SpringBootTest(properties = {"app.seed.enabled=false", "app.auth.otp.resend-cooldown-seconds=0"})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    static final String EXISTING = "9000000001";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSeeder seeder;
    @Autowired CustomerRepository customers;

    @BeforeEach
    void setUp() {
        seeder.wipe();
        customers.insertWithId(1L, "Test", EXISTING, "Sector 45", "Gurugram", null, Instant.now());
    }

    @Test
    @DisplayName("New user: request OTP (registered=false) -> register -> token works on protected APIs")
    void signupFlow() throws Exception {
        JsonNode otp = call(post("/api/auth/otp/request"), "{\"phone\":\"+91 98765 00001\"}", 200);
        assertThat(otp.path("registered").asBoolean()).isFalse();
        assertThat(otp.path("phone").asText()).isEqualTo("9876500001");

        JsonNode reg = call(post("/api/auth/register"),
                "{\"phone\":\"9876500001\",\"otp\":\"123456\",\"name\":\"Riya\",\"area\":\"HSR Layout\",\"city\":\"Bengaluru\"}", 201);
        assertThat(reg.path("newUser").asBoolean()).isTrue();
        assertThat(reg.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(reg.path("customer").path("customerId").asLong()).isGreaterThanOrEqualTo(100001L);
        String token = reg.path("accessToken").asText();

        JsonNode me = authed(get("/api/auth/me"), token, 200);
        assertThat(me.path("name").asText()).isEqualTo("Riya");
        assertThat(me.path("phone").asText()).isEqualTo("9876500001");
        authed(get("/api/cart"), token, 200);
        authed(get("/api/feedback-prompts/next"), token, 200);
    }

    @Test
    @DisplayName("Existing user: wrong OTP -> 400 with attempts left; right OTP -> token; OTP can't be reused")
    void loginFlow() throws Exception {
        JsonNode otp = call(post("/api/auth/otp/request"), "{\"phone\":\"" + EXISTING + "\"}", 200);
        assertThat(otp.path("registered").asBoolean()).isTrue();

        JsonNode wrong = call(post("/api/auth/login"), "{\"phone\":\"" + EXISTING + "\",\"otp\":\"000000\"}", 400);
        assertThat(wrong.path("code").asText()).isEqualTo("OTP_INVALID");
        assertThat(wrong.path("message").asText()).contains("4 attempt");

        JsonNode ok = call(post("/api/auth/login"), "{\"phone\":\"" + EXISTING + "\",\"otp\":\"123456\"}", 200);
        assertThat(ok.path("newUser").asBoolean()).isFalse();
        assertThat(ok.path("customer").path("customerId").asLong()).isEqualTo(1L);
        authed(get("/api/feedback-prompts/next"), ok.path("accessToken").asText(), 200);

        JsonNode reused = call(post("/api/auth/login"), "{\"phone\":\"" + EXISTING + "\",\"otp\":\"123456\"}", 400);
        assertThat(reused.path("code").asText()).isEqualTo("OTP_NOT_REQUESTED");
    }

    @Test
    @DisplayName("5 wrong OTPs lock that code (429); even the right code then fails")
    void tooManyAttempts() throws Exception {
        call(post("/api/auth/otp/request"), "{\"phone\":\"" + EXISTING + "\"}", 200);
        for (int i = 0; i < 4; i++) {
            call(post("/api/auth/login"), "{\"phone\":\"" + EXISTING + "\",\"otp\":\"111111\"}", 400);
        }
        JsonNode locked = call(post("/api/auth/login"), "{\"phone\":\"" + EXISTING + "\",\"otp\":\"111111\"}", 429);
        assertThat(locked.path("code").asText()).isEqualTo("OTP_TOO_MANY_ATTEMPTS");
        call(post("/api/auth/login"), "{\"phone\":\"" + EXISTING + "\",\"otp\":\"123456\"}", 400);
    }

    @Test
    @DisplayName("At most 5 OTP requests per hour per phone")
    void hourlyLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            call(post("/api/auth/otp/request"), "{\"phone\":\"" + EXISTING + "\"}", 200);
        }
        JsonNode r = call(post("/api/auth/otp/request"), "{\"phone\":\"" + EXISTING + "\"}", 429);
        assertThat(r.path("code").asText()).isEqualTo("OTP_LIMIT_REACHED");
    }

    @Test
    @DisplayName("Login with an unregistered number -> 404; register an existing number -> 409; bad phone -> 400")
    void wrongPaths() throws Exception {
        call(post("/api/auth/otp/request"), "{\"phone\":\"9876500002\"}", 200);
        JsonNode notReg = call(post("/api/auth/login"), "{\"phone\":\"9876500002\",\"otp\":\"123456\"}", 404);
        assertThat(notReg.path("code").asText()).isEqualTo("NOT_REGISTERED");

        JsonNode dup = call(post("/api/auth/register"),
                "{\"phone\":\"" + EXISTING + "\",\"otp\":\"123456\",\"name\":\"X\",\"area\":\"A\",\"city\":\"C\"}", 409);
        assertThat(dup.path("code").asText()).isEqualTo("PHONE_ALREADY_REGISTERED");

        JsonNode bad = call(post("/api/auth/otp/request"), "{\"phone\":\"12345\"}", 400);
        assertThat(bad.path("code").asText()).isEqualTo("INVALID_PHONE");
    }

    @Test
    @DisplayName("Editing the token (e.g. changing the customer id) or sending garbage -> 401")
    void tamperedToken() throws Exception {
        call(post("/api/auth/otp/request"), "{\"phone\":\"" + EXISTING + "\"}", 200);
        String token = call(post("/api/auth/login"), "{\"phone\":\"" + EXISTING + "\",\"otp\":\"123456\"}", 200)
                .path("accessToken").asText();

        // Swap the payload's "sub" (customer id) from 1 to 2, keep the original signature.
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"sub\":\"1\"", "\"sub\":\"2\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        JsonNode r = authed(get("/api/cart"), forged, 401);
        assertThat(r.path("code").asText()).isEqualTo("INVALID_TOKEN");
        authed(get("/api/cart"), "not-a-token", 401);
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                          String body, int expected) throws Exception {
        MvcResult r = mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expected);
        return json.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode authed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                            String token, int expected) throws Exception {
        MvcResult r = mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expected);
        String body = r.getResponse().getContentAsString();
        return body.isEmpty() ? json.createObjectNode() : json.readTree(body);
    }
}
