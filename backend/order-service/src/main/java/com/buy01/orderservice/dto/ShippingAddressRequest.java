package com.buy01.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 250) String line1,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String country
) {
}
