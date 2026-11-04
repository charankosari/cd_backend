package com.countrydelight.feedback.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;


@Component
public class OtpCodeGenerator {

    private final SecureRandom random = new SecureRandom();
    private final AuthProperties.Otp config;

    public OtpCodeGenerator(AuthProperties props) {
        this.config = props.otp();
    }

    public String newCode() {
        if (config.usesFixedCode()) {
            return config.fixedCode();
        }
        int bound = (int) Math.pow(10, config.length());
        int n = random.nextInt(bound);
        return String.format("%0" + config.length() + "d", n);
    }
}
