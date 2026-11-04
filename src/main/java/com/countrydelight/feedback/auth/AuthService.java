package com.countrydelight.feedback.auth;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.service.CustomerService;
import com.countrydelight.feedback.web.dto.AuthDtos.AuthResponse;
import com.countrydelight.feedback.web.dto.AuthDtos.CustomerProfile;
import com.countrydelight.feedback.web.dto.AuthDtos.OtpRequestResponse;
import com.countrydelight.feedback.web.dto.AuthDtos.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final OtpService otpService;
    private final TokenService tokenService;
    private final CustomerService customerService;
    private final AuthProperties props;

    public AuthService(OtpService otpService, TokenService tokenService, CustomerService customerService,
                       AuthProperties props) {
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.customerService = customerService;
        this.props = props;
    }

    public OtpRequestResponse requestOtp(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        OtpService.OtpIssued issued = otpService.request(phone);
        boolean registered = customerService.findByPhone(phone).isPresent();
        return new OtpRequestResponse(phone, registered, props.otp().length(), issued.expiresInSeconds(),
                issued.resendAfterSeconds(), "OTP sent to " + PhoneNumbers.mask(phone));
    }

    public AuthResponse login(String rawPhone, String otp) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Customer customer = customerService.findByPhone(phone)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_REGISTERED",
                        "No account for this number. Sign up first"));
        otpService.verify(phone, otp);
        return toResponse(customer, false);
    }

    public AuthResponse register(RegisterRequest req) {
        String phone = PhoneNumbers.normalize(req.phone());
        if (customerService.findByPhone(phone).isPresent()) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This number already has an account. Log in instead");
        }
        otpService.verify(phone, req.otp());
        Customer created = customerService.create(phone, req.name(), req.area(), req.city());
        return toResponse(created, true);
    }

    public CustomerProfile me(Long customerId) {
        return profile(customerService.get(customerId));
    }

    private AuthResponse toResponse(Customer customer, boolean newUser) {
        TokenService.IssuedToken token = tokenService.issueFor(customer);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), token.expiresAt(), newUser,
                profile(customer));
    }

    private static CustomerProfile profile(Customer c) {
        return new CustomerProfile(c.getId(), c.getName(), c.getPhone(), c.getArea(), c.getCity());
    }
}
