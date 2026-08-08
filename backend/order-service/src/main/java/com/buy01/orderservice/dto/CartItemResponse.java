package com.buy01.orderservice.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        String productId,
        String sellerId,
        String name,
        String imageUrl,
        BigDecimal price,
        int quantity,
        BigDecimal subtotal
) {
}
