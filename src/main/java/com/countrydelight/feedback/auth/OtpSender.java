package com.countrydelight.feedback.auth;


public interface OtpSender {

    void send(String phone, String code);
}
