package com.buy01.orderservice.dto;

import com.buy01.orderservice.model.OrderStatus;
import com.buy01.orderservice.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String buyerId,
        String buyerName,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        OrderStatus status,
        PaymentMethod paymentMethod,
        ShippingAddressResponse shippingAddress,
        List<StatusChangeResponse> statusHistory,
        Instant createdAt,
        Instant updatedAt
) {
}
