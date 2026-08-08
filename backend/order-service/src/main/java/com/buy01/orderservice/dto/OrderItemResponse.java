package com.buy01.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        String sellerId,
        String sellerName,
        String name,
        String imageUrl,
        BigDecimal price,
        int quantity,
        String category,
        BigDecimal subtotal
) {
}
