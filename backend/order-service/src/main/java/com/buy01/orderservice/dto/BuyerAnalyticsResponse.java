package com.buy01.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record BuyerAnalyticsResponse(
        BigDecimal totalSpent,
        int ordersCount,
        List<ProductStat> mostBoughtProducts,
        List<CategoryStat> topCategories
) {
}
