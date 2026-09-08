package com.buy01.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buy01.orderservice.dto.BuyerAnalyticsResponse;
import com.buy01.orderservice.dto.CartResponse;
import com.buy01.orderservice.dto.CheckoutRequest;
import com.buy01.orderservice.dto.OrderResponse;
import com.buy01.orderservice.dto.OrderStatusUpdateRequest;
import com.buy01.orderservice.dto.PageResponse;
import com.buy01.orderservice.dto.SellerAnalyticsResponse;
import com.buy01.orderservice.dto.ShippingAddressRequest;
import com.buy01.orderservice.model.OrderStatus;
import com.buy01.security.AuthenticatedUser;
import com.buy01.orderservice.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final OrderController controller = new OrderController(orderService);
    private final Authentication authentication = mock(Authentication.class);
    private final AuthenticatedUser user = new AuthenticatedUser("buyer-1", "buyer@example.com", "CLIENT");

    @BeforeEach
    void setUp() {
        when(authentication.getPrincipal()).thenReturn(user);
    }

    @Test
    void checkoutDelegatesToOrderService() {
        CheckoutRequest request = new CheckoutRequest(
                new ShippingAddressRequest("Buyer", "12345", "1 Main St", "City", "00000", "Country"));
        OrderResponse expected = sampleOrder();
        when(orderService.checkout(user, "Bearer token", request)).thenReturn(expected);

        OrderResponse response = controller.checkout(authentication, "Bearer token", request);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void getMyOrdersDelegatesToOrderService() {
        PageResponse<OrderResponse> expected = PageResponse.of(List.of(), 0, 20, 0);
        when(orderService.getBuyerOrders(user, null, null, null, null, 0, 20)).thenReturn(expected);

        PageResponse<OrderResponse> response = controller.getMyOrders(authentication, null, null, null, null, 0, 20);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void getSellerOrdersDelegatesToOrderService() {
        PageResponse<OrderResponse> expected = PageResponse.of(List.of(), 0, 20, 0);
        when(orderService.getSellerOrders(user, OrderStatus.PENDING, "phone", null, null, 0, 20)).thenReturn(expected);

        PageResponse<OrderResponse> response =
                controller.getSellerOrders(authentication, OrderStatus.PENDING, "phone", null, null, 0, 20);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void getBuyerAnalyticsDelegatesToOrderService() {
        BuyerAnalyticsResponse expected = new BuyerAnalyticsResponse(BigDecimal.TEN, 1, List.of(), List.of());
        when(orderService.getBuyerAnalytics(user)).thenReturn(expected);

        assertThat(controller.getBuyerAnalytics(authentication)).isSameAs(expected);
    }

    @Test
    void getSellerAnalyticsDelegatesToOrderService() {
        SellerAnalyticsResponse expected = new SellerAnalyticsResponse(BigDecimal.TEN, 1, 1, List.of());
        when(orderService.getSellerAnalytics(user)).thenReturn(expected);

        assertThat(controller.getSellerAnalytics(authentication)).isSameAs(expected);
    }

    @Test
    void getOrderDelegatesToOrderService() {
        OrderResponse expected = sampleOrder();
        when(orderService.getOrder(user, "order-1")).thenReturn(expected);

        assertThat(controller.getOrder("order-1", authentication)).isSameAs(expected);
    }

    @Test
    void cancelOrderDelegatesToOrderService() {
        OrderResponse expected = sampleOrder();
        when(orderService.cancelOrder(user, "order-1", "Bearer token")).thenReturn(expected);

        assertThat(controller.cancelOrder("order-1", authentication, "Bearer token")).isSameAs(expected);
    }

    @Test
    void redoOrderDelegatesToOrderService() {
        CartResponse expected = new CartResponse(List.of(), 0, BigDecimal.ZERO);
        when(orderService.redoOrder(user, "order-1")).thenReturn(expected);

        assertThat(controller.redoOrder("order-1", authentication)).isSameAs(expected);
    }

    @Test
    void removeOrderDelegatesToOrderService() {
        controller.removeOrder("order-1", authentication);

        verify(orderService).removeOrder(user, "order-1");
    }

    @Test
    void updateOrderStatusDelegatesToOrderService() {
        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);
        OrderResponse expected = sampleOrder();
        when(orderService.updateOrderStatus(eq(user), eq("order-1"), any())).thenReturn(expected);

        assertThat(controller.updateOrderStatus("order-1", authentication, request)).isSameAs(expected);
    }

    private OrderResponse sampleOrder() {
        return new OrderResponse(
                "order-1", "buyer-1", "buyer@example.com", List.of(), BigDecimal.TEN,
                OrderStatus.PENDING, null, null, List.of(), null, null
        );
    }
}
