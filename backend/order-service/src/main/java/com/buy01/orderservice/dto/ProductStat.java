package com.buy01.orderservice.dto;

import java.math.BigDecimal;

public record ProductStat(
        String productId,
        String name,
        int unitsSold,
        BigDecimal revenue
) {
}
