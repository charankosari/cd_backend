package com.countrydelight.feedback.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a controller parameter to receive the logged-in customer's id, taken from the
 * verified login token:
 * <pre>
 *   public CartResponse view(@CurrentCustomer Long customerId) { ... }
 * </pre>
 * Filled in by CurrentCustomerArgumentResolver.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentCustomer {
}
