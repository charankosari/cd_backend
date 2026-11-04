package com.countrydelight.feedback.auth;

/**
 * Delivers an OTP to the user. Today the only implementation is LoggingOtpSender (no SMS).
 * To send real SMS later, write e.g. Msg91OtpSender implements OtpSender, make it the Spring bean
 * (and remove @Component from LoggingOtpSender). Nothing else in the code changes.
 */
public interface OtpSender {

    void send(String phone, String code);
}
