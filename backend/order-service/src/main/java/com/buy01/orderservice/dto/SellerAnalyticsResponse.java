package com.buy01.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record SellerAnalyticsResponse(
        BigDecimal totalRevenue,
        int unitsSold,
        int ordersCount,
        List<ProductStat> bestSellingProducts
) {
}
