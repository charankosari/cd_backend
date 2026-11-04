package com.countrydelight.feedback.auth;

import com.countrydelight.feedback.common.ApiException;

import java.util.regex.Pattern;

/** Indian mobile number helpers. Everything is stored as 10 digits, e.g. "9876543210". */
public final class PhoneNumbers {

    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    private PhoneNumbers() {
    }

    /** Accepts "98765 43210", "+91 9876543210", "09876543210" and returns "9876543210". */
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

    /** "9876543210" becomes "******3210", safe to print in logs. */
    public static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }
}
