package com.buy01.orderservice.dto;

import java.math.BigDecimal;

public record CategoryStat(
        String category,
        BigDecimal amount
) {
}
