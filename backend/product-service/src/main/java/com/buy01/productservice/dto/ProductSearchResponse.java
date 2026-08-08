package com.buy01.productservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<String> categories,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
