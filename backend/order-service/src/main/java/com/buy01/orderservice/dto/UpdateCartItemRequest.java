package com.buy01.orderservice.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCartItemRequest(
        @PositiveOrZero int quantity
) {
}
