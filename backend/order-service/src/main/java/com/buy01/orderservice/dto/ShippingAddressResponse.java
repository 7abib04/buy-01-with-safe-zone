package com.buy01.orderservice.dto;

public record ShippingAddressResponse(
        String fullName,
        String phone,
        String line1,
        String city,
        String postalCode,
        String country
) {
}
