package com.buy01.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSnapshotResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        int quantity,
        String sellerId,
        String category,
        List<String> imageUrls
) {
}
