package com.buy01.orderservice.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
        String eventType,
        String orderId,
        String buyerId,
        String status,
        BigDecimal totalAmount,
        Instant occurredAt
) {
}
