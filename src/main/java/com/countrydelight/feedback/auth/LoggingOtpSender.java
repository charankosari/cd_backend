package com.countrydelight.feedback.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stand-in for an SMS gateway: writes a log line instead of sending a message. Never logs the code. */
@Component
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(String phone, String code) {
        log.info("OTP issued for {} (no SMS provider configured: not actually sent)", PhoneNumbers.mask(phone));
    }
}
