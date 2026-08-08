package com.buy01.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull @Valid ShippingAddressRequest shippingAddress
) {
}
