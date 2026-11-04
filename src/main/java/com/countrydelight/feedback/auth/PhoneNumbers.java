package com.countrydelight.feedback.auth;

import com.countrydelight.feedback.common.ApiException;

import java.util.regex.Pattern;

public final class PhoneNumbers {

    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Phone number is required");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!INDIAN_MOBILE.matcher(digits).matches()) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
        }
        return digits;
    }

    public static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }
}
