package com.countrydelight.feedback.web;

import com.countrydelight.feedback.auth.AuthService;
import com.countrydelight.feedback.auth.CurrentCustomer;
import com.countrydelight.feedback.web.dto.AuthDtos.AuthResponse;
import com.countrydelight.feedback.web.dto.AuthDtos.CustomerProfile;
import com.countrydelight.feedback.web.dto.AuthDtos.LoginRequest;
import com.countrydelight.feedback.web.dto.AuthDtos.OtpRequest;
import com.countrydelight.feedback.web.dto.AuthDtos.OtpRequestResponse;
import com.countrydelight.feedback.web.dto.AuthDtos.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Open. Sends an OTP (today: always the fixed code, 123456). */
    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest body) {
        return auth.requestOtp(body.phone());
    }

    /** Open. Existing user: phone + OTP gives a token. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body) {
        return auth.login(body.phone(), body.otp());
    }

    /** Open. New user: phone + OTP + profile creates the account and returns a token. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest body) {
        return auth.register(body);
    }

    /** Needs a token. Who am I? */
    @GetMapping("/me")
    public CustomerProfile me(@CurrentCustomer Long customerId) {
        return auth.me(customerId);
    }
}
