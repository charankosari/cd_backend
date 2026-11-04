package com.countrydelight.feedback;

import com.countrydelight.feedback.auth.AuthProperties;
import com.countrydelight.feedback.auth.OtpCodeGenerator;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.seed.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"app.seed.enabled=false"})
@AutoConfigureMockMvc
class OtpRulesTest {

    @Autowired MockMvc mvc;
    @Autowired DataSeeder seeder;
    @Autowired CustomerRepository customers;

    @Test
    @DisplayName("Second OTP request within 30 seconds -> 429 OTP_RESEND_TOO_SOON")
    void resendCooldown() throws Exception {
        seeder.wipe();
        String body = "{\"phone\":\"9876500009\"}";
        assertThat(mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        var second = mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("OTP_RESEND_TOO_SOON");
    }

    @Test
    @DisplayName("Random mode (no fixed code): 6 digits, keeps leading zeros, not always the same")
    void randomCodes() {
        AuthProperties props = new AuthProperties(null, new AuthProperties.Otp("", 6, 300, 30, 5, 5));
        OtpCodeGenerator gen = new OtpCodeGenerator(props);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String code = gen.newCode();
            assertThat(code).matches("\\d{6}");
            seen.add(code);
        }
        assertThat(seen.size()).isGreaterThan(40);
    }

    @Test
    @DisplayName("Fixed mode: always the configured code")
    void fixedCode() {
        AuthProperties props = new AuthProperties(null, new AuthProperties.Otp("123456", 6, 300, 30, 5, 5));
        assertThat(new OtpCodeGenerator(props).newCode()).isEqualTo("123456");
    }
}
