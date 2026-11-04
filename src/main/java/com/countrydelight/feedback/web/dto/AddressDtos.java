package com.countrydelight.feedback.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AddressDtos {

    private AddressDtos() {
    }

    public record AddressRequest(@NotBlank @Size(max = 30) String label,
                                 @NotBlank @Size(max = 120) String line1,
                                 @Size(max = 120) String line2,
                                 @Size(max = 80) String landmark,
                                 @NotBlank @Size(max = 80) String area,
                                 @NotBlank @Size(max = 60) String city,
                                 @NotBlank @Pattern(regexp = "[1-9]\\d{5}", message = "must be a 6-digit pincode") String pincode,
                                 Boolean makeDefault) {
    }

    public record AddressView(Long addressId,
                              String label,
                              String line1,
                              String line2,
                              String landmark,
                              String area,
                              String city,
                              String pincode,
                              boolean isDefault,
                              String fullAddress) {
    }

    public record AddressList(List<AddressView> addresses, Long defaultAddressId) {
    }
}
