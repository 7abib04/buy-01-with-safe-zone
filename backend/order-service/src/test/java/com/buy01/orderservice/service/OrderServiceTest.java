package com.buy01.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buy01.orderservice.client.ProductServiceClient;
import com.buy01.orderservice.dto.CartItemRequest;
import com.buy01.orderservice.dto.CartResponse;
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
import com.buy01.security.AuthenticatedUser;
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

        CheckoutRequest request = new CheckoutRequest(address());
        assertThatThrownBy(() -> orderService.checkout(buyer, "Bearer token", request))
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

        CheckoutRequest request = new CheckoutRequest(address());
        assertThatThrownBy(() -> orderService.checkout(buyer, "Bearer token", request))
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

        CheckoutRequest request = new CheckoutRequest(address());
        assertThatThrownBy(() -> orderService.checkout(buyer, "Bearer token", request))
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

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> orderService.updateOrderStatus(seller, "order-1", request))
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

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.updateOrderStatus(otherSeller, "order-1", request))
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

    private OrderItem itemFor(String sellerId, String productId, BigDecimal subtotal) {
        OrderItem item = new OrderItem();
        item.setSellerId(sellerId);
        item.setProductId(productId);
        item.setName("Item " + productId);
        item.setQuantity(1);
        item.setSubtotal(subtotal);
        item.setCategory("ELECTRONICS");
        return item;
    }

    private Order orderForBuyer(OrderStatus status) {
        Order order = new Order();
        order.setId("order-1");
        order.setBuyerId("buyer-1");
        order.setStatus(status);
        return order;
    }

    private void stubFindableOrder(Order order) {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    }

    private void stubOwnedOrder(Order order) {
        when(orderRepository.findByIdAndBuyerId("order-1", "buyer-1")).thenReturn(Optional.of(order));
    }

    @Test
    void getOrderReturnsFullOrderForOwner() {
        Order order = orderForBuyer(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setItems(List.of(itemFor("seller-1", "product-1", new BigDecimal("10.00"))));
        stubFindableOrder(order);

        var response = orderService.getOrder(buyer, "order-1");

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void getOrderScopesItemsForInvolvedSeller() {
        Order order = orderForBuyer(OrderStatus.PENDING);
        order.setItems(List.of(
                itemFor("seller-1", "product-1", new BigDecimal("10.00")),
                itemFor("seller-2", "product-2", new BigDecimal("20.00"))
        ));
        stubFindableOrder(order);

        AuthenticatedUser seller = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        var response = orderService.getOrder(seller, "order-1");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sellerId()).isEqualTo("seller-1");
        assertThat(response.totalAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void getOrderRejectsUninvolvedUser() {
        Order order = orderForBuyer(OrderStatus.PENDING);
        order.setItems(List.of(itemFor("seller-1", "product-1", BigDecimal.TEN)));
        stubFindableOrder(order);

        AuthenticatedUser stranger = new AuthenticatedUser("stranger-1", "stranger@example.com", "CLIENT");

        assertThatThrownBy(() -> orderService.getOrder(stranger, "order-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelOrderRestoresStockAndMarksCancelled() {
        Order order = orderForBuyer(OrderStatus.PENDING);
        order.setItems(List.of(itemFor("seller-1", "product-1", BigDecimal.TEN)));
        order.getItems().get(0).setQuantity(2);

        stubOwnedOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = orderService.cancelOrder(buyer, "order-1", "Bearer token");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(productServiceClient).adjustStock("Bearer token", "product-1", 2);
        verify(orderEventPublisher).publishCancelled(any(Order.class));
    }

    @Test
    void cancelOrderSwallowsRestockFailures() {
        Order order = orderForBuyer(OrderStatus.CONFIRMED);
        order.setItems(List.of(itemFor("seller-1", "product-1", BigDecimal.TEN)));

        stubOwnedOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("product deleted"))
                .when(productServiceClient).adjustStock(anyString(), anyString(), anyInt());

        var response = orderService.cancelOrder(buyer, "order-1", "Bearer token");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void redoOrderAddsItemsBackToCartSkippingFailures() {
        Order order = orderForBuyer(OrderStatus.PENDING);
        order.setItems(List.of(
                itemFor("seller-1", "product-1", BigDecimal.TEN),
                itemFor("seller-2", "product-2", BigDecimal.TEN)
        ));
        stubOwnedOrder(order);

        CartResponse cart = new CartResponse(List.of(), 0, BigDecimal.ZERO);
        when(cartService.getCart("buyer-1")).thenReturn(cart);
        when(cartService.addItem(eq("buyer-1"), any(CartItemRequest.class)))
                .thenThrow(new RuntimeException("out of stock"))
                .thenReturn(cart);

        CartResponse result = orderService.redoOrder(buyer, "order-1");

        assertThat(result).isSameAs(cart);
        verify(cartService, times(2)).addItem(eq("buyer-1"), any(CartItemRequest.class));
    }

    @Test
    void removeOrderHidesCancelledOrder() {
        Order order = orderForBuyer(OrderStatus.CANCELLED);
        stubOwnedOrder(order);

        orderService.removeOrder(buyer, "order-1");

        assertThat(order.isHiddenByBuyer()).isTrue();
        verify(orderRepository).save(order);
    }

    @Test
    void removeOrderRejectsOrderStillInProgress() {
        Order order = orderForBuyer(OrderStatus.PENDING);
        stubOwnedOrder(order);

        assertThatThrownBy(() -> orderService.removeOrder(buyer, "order-1"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void getSellerOrdersReturnsPaginatedResults() {
        Order order = new Order();
        order.setId("order-1");
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setItems(List.of(itemFor("seller-1", "product-1", BigDecimal.TEN)));

        AuthenticatedUser seller = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        when(orderRepository.findByItemsSellerIdOrderByCreatedAtDesc("seller-1")).thenReturn(List.of(order));

        var page = orderService.getSellerOrders(seller, null, null, null, null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void updateOrderStatusAdvancesToNextStatusInSequence() {
        Order order = new Order();
        order.setId("order-1");
        order.setStatus(OrderStatus.PENDING);
        OrderItem item = itemFor("seller-1", "product-1", BigDecimal.TEN);
        order.setItems(List.of(item));

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatedUser seller = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        var response = orderService.updateOrderStatus(seller, "order-1", new OrderStatusUpdateRequest(OrderStatus.CONFIRMED));

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderEventPublisher).publishStatusChanged(any(Order.class));
    }

    @Test
    void getSellerAnalyticsAggregatesRevenueAndUnitsSold() {
        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);
        OrderItem sellerItem = itemFor("seller-1", "product-1", new BigDecimal("30.00"));
        sellerItem.setQuantity(3);
        OrderItem otherSellerItem = itemFor("seller-2", "product-2", new BigDecimal("99.00"));
        order.setItems(List.of(sellerItem, otherSellerItem));

        AuthenticatedUser seller = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        when(orderRepository.findByItemsSellerIdOrderByCreatedAtDesc("seller-1")).thenReturn(List.of(order));

        var analytics = orderService.getSellerAnalytics(seller);

        assertThat(analytics.totalRevenue()).isEqualByComparingTo("30.00");
        assertThat(analytics.unitsSold()).isEqualTo(3);
        assertThat(analytics.ordersCount()).isEqualTo(1);
        assertThat(analytics.bestSellingProducts()).hasSize(1);
    }

    @Test
    void getBuyerOrdersFiltersByStatusAndQueryAndExcludesHidden() {
        Order visible = new Order();
        visible.setId("order-1");
        visible.setBuyerId("buyer-1");
        visible.setStatus(OrderStatus.PENDING);
        visible.setCreatedAt(Instant.now());
        visible.setItems(List.of(itemFor("seller-1", "phone-case", BigDecimal.TEN)));
        visible.setHiddenByBuyer(false);

        Order hidden = new Order();
        hidden.setId("order-2");
        hidden.setBuyerId("buyer-1");
        hidden.setStatus(OrderStatus.PENDING);
        hidden.setCreatedAt(Instant.now());
        hidden.setItems(List.of());
        hidden.setHiddenByBuyer(true);

        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc("buyer-1")).thenReturn(List.of(visible, hidden));

        var page = orderService.getBuyerOrders(buyer, OrderStatus.PENDING, "case", null, null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo("order-1");
    }
}
