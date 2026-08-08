package com.buy01.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buy01.orderservice.client.ProductServiceClient;
import com.buy01.orderservice.dto.CheckoutRequest;
import com.buy01.orderservice.dto.OrderResponse;
import com.buy01.orderservice.dto.OrderStatusUpdateRequest;
import com.buy01.orderservice.dto.ProductSnapshotResponse;
import com.buy01.orderservice.dto.ShippingAddressRequest;
import com.buy01.orderservice.event.OrderEventPublisher;
import com.buy01.orderservice.exception.CartEmptyException;
import com.buy01.orderservice.exception.InsufficientStockException;
import com.buy01.orderservice.exception.InvalidOrderStatusTransitionException;
import com.buy01.orderservice.model.Cart;
import com.buy01.orderservice.model.CartItem;
import com.buy01.orderservice.model.Order;
import com.buy01.orderservice.model.OrderItem;
import com.buy01.orderservice.model.OrderStatus;
import com.buy01.orderservice.repository.CartRepository;
import com.buy01.orderservice.repository.OrderRepository;
import com.buy01.orderservice.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private CartService cartService;

    @InjectMocks
    private OrderService orderService;

    private final AuthenticatedUser buyer = new AuthenticatedUser("buyer-1", "buyer@example.com", "CLIENT");

    private ShippingAddressRequest address() {
        return new ShippingAddressRequest("Buyer One", "12345678", "1 Main St", "City", "00000", "Country");
    }

    @Test
    void checkoutCreatesOrderAndDecrementsStock() {
        Cart cart = new Cart();
        cart.setBuyerId("buyer-1");
        cart.getItems().add(new CartItem("product-1", "seller-1", "Phone", null, new BigDecimal("100.00"), 2));

        when(cartRepository.findByBuyerId("buyer-1")).thenReturn(Optional.of(cart));
        when(productServiceClient.getProduct("product-1")).thenReturn(new ProductSnapshotResponse(
                "product-1", "Phone", "desc", new BigDecimal("100.00"), 5, "seller-1", "ELECTRONICS", List.of()
        ));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("order-1");
            return order;
        });

        OrderResponse response = orderService.checkout(buyer, "Bearer token", new CheckoutRequest(address()));

        assertThat(response.id()).isEqualTo("order-1");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        verify(productServiceClient).adjustStock("Bearer token", "product-1", -2);
        verify(cartService).clearCart("buyer-1");
        verify(orderEventPublisher).publishCreated(any(Order.class));
    }

    @Test
    void checkoutRejectsEmptyCart() {
        when(cartRepository.findByBuyerId("buyer-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(buyer, "Bearer token", new CheckoutRequest(address())))
                .isInstanceOf(CartEmptyException.class);
    }

    @Test
    void checkoutRejectsInsufficientStock() {
        Cart cart = new Cart();
        cart.setBuyerId("buyer-1");
        cart.getItems().add(new CartItem("product-1", "seller-1", "Phone", null, new BigDecimal("100.00"), 5));

        when(cartRepository.findByBuyerId("buyer-1")).thenReturn(Optional.of(cart));
        when(productServiceClient.getProduct("product-1")).thenReturn(new ProductSnapshotResponse(
                "product-1", "Phone", "desc", new BigDecimal("100.00"), 1, "seller-1", "ELECTRONICS", List.of()
        ));

        assertThatThrownBy(() -> orderService.checkout(buyer, "Bearer token", new CheckoutRequest(address())))
                .isInstanceOf(InsufficientStockException.class);

        verify(productServiceClient, never()).adjustStock(anyString(), anyString(), anyInt());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void checkoutRollsBackDecrementedStockWhenALaterItemFails() {
        Cart cart = new Cart();
        cart.setBuyerId("buyer-1");
        cart.getItems().add(new CartItem("product-1", "seller-1", "Phone", null, new BigDecimal("100.00"), 1));
        cart.getItems().add(new CartItem("product-2", "seller-2", "Case", null, new BigDecimal("10.00"), 1));

        when(cartRepository.findByBuyerId("buyer-1")).thenReturn(Optional.of(cart));
        when(productServiceClient.getProduct("product-1")).thenReturn(new ProductSnapshotResponse(
                "product-1", "Phone", "desc", new BigDecimal("100.00"), 5, "seller-1", "ELECTRONICS", List.of()
        ));
        when(productServiceClient.getProduct("product-2")).thenReturn(new ProductSnapshotResponse(
                "product-2", "Case", "desc", new BigDecimal("10.00"), 5, "seller-2", "ELECTRONICS", List.of()
        ));
        doAnswer(invocation -> {
            String productId = invocation.getArgument(1);
            if ("product-2".equals(productId)) {
                throw new RuntimeException("boom");
            }
            return null;
        }).when(productServiceClient).adjustStock(eq("Bearer token"), anyString(), anyInt());

        assertThatThrownBy(() -> orderService.checkout(buyer, "Bearer token", new CheckoutRequest(address())))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(productServiceClient).adjustStock("Bearer token", "product-1", -1);
        verify(productServiceClient).adjustStock("Bearer token", "product-1", 1);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void cancelOrderRestoresStockAndRejectsShippedOrders() {
        Order shippedOrder = new Order();
        shippedOrder.setId("order-1");
        shippedOrder.setBuyerId("buyer-1");
        shippedOrder.setStatus(OrderStatus.SHIPPED);
        shippedOrder.getStatusHistory().add(new com.buy01.orderservice.model.StatusChange(OrderStatus.SHIPPED, Instant.now(), "seller-1"));

        when(orderRepository.findByIdAndBuyerId("order-1", "buyer-1")).thenReturn(Optional.of(shippedOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(buyer, "order-1", "Bearer token"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void updateOrderStatusRejectsSkippingAheadInSequence() {
        Order order = new Order();
        order.setId("order-1");
        order.setStatus(OrderStatus.PENDING);
        OrderItem item = new OrderItem();
        item.setSellerId("seller-1");
        order.setItems(List.of(item));

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        AuthenticatedUser seller = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");

        assertThatThrownBy(() -> orderService.updateOrderStatus(seller, "order-1", new OrderStatusUpdateRequest(OrderStatus.SHIPPED)))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void updateOrderStatusRejectsUninvolvedSeller() {
        Order order = new Order();
        order.setId("order-1");
        order.setStatus(OrderStatus.PENDING);
        OrderItem item = new OrderItem();
        item.setSellerId("seller-1");
        order.setItems(List.of(item));

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        AuthenticatedUser otherSeller = new AuthenticatedUser("seller-2", "seller2@example.com", "SELLER");

        assertThatThrownBy(() -> orderService.updateOrderStatus(otherSeller, "order-1", new OrderStatusUpdateRequest(OrderStatus.CONFIRMED)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getBuyerAnalyticsAggregatesSpendAndTopProducts() {
        Order order = new Order();
        order.setBuyerId("buyer-1");
        order.setStatus(OrderStatus.DELIVERED);
        order.setTotalAmount(new BigDecimal("150.00"));

        OrderItem item1 = new OrderItem();
        item1.setProductId("product-1");
        item1.setName("Phone");
        item1.setQuantity(1);
        item1.setCategory("ELECTRONICS");
        item1.setSubtotal(new BigDecimal("100.00"));

        OrderItem item2 = new OrderItem();
        item2.setProductId("product-2");
        item2.setName("Case");
        item2.setQuantity(1);
        item2.setCategory("ACCESSORIES");
        item2.setSubtotal(new BigDecimal("50.00"));

        order.setItems(List.of(item1, item2));

        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc("buyer-1")).thenReturn(List.of(order));

        var analytics = orderService.getBuyerAnalytics(buyer);

        assertThat(analytics.totalSpent()).isEqualByComparingTo("150.00");
        assertThat(analytics.ordersCount()).isEqualTo(1);
        assertThat(analytics.mostBoughtProducts()).hasSize(2);
        assertThat(analytics.topCategories()).hasSize(2);
    }
}
