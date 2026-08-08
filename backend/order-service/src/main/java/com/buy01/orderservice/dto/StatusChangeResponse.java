package com.buy01.orderservice.dto;

import com.buy01.orderservice.model.OrderStatus;
import java.time.Instant;

public record StatusChangeResponse(
        OrderStatus status,
        Instant changedAt,
        String changedBy
) {
}
