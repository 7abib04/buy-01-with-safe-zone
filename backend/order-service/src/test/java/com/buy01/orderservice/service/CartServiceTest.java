package com.buy01.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.buy01.orderservice.client.ProductServiceClient;
import com.buy01.orderservice.dto.CartItemRequest;
import com.buy01.orderservice.dto.CartResponse;
import com.buy01.orderservice.dto.ProductSnapshotResponse;
import com.buy01.orderservice.dto.UpdateCartItemRequest;
import com.buy01.orderservice.exception.InsufficientStockException;
import com.buy01.orderservice.model.Cart;
import com.buy01.orderservice.repository.CartRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private CartService cartService;

    @Test
    void addItemCreatesCartWhenNoneExists() {
        when(cartRepository.findByBuyerId("buyer-1")).thenReturn(Optional.empty());
        when(productServiceClient.getProduct("product-1")).thenReturn(new ProductSnapshotResponse(
                "product-1", "Phone", "desc", new BigDecimal("100.00"), 10, "seller-1", "ELECTRONICS",
                List.of("http://localhost/img.png")
        ));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem("buyer-1", new CartItemRequest("product-1", 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void addItemRejectsQuantityBeyondAvailableStock() {
        when(cartRepository.findByBuyerId("buyer-1")).thenReturn(Optional.empty());
        when(productServiceClient.getProduct("product-1")).thenReturn(new ProductSnapshotResponse(
                "product-1", "Phone", "desc", new BigDecimal("100.00"), 1, "seller-1", "ELECTRONICS", List.of()
        ));

        assertThatThrownBy(() -> cartService.addItem("buyer-1", new CartItemRequest("product-1", 5)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void updateItemQuantityToZeroRemovesItem() {
        Cart cart = new Cart();
        cart.setBuyerId("buyer-1");
        cart.getItems().add(new com.buy01.orderservice.model.CartItem(
                "product-1", "seller-1", "Phone", null, new BigDecimal("100.00"), 2
        ));

        when(cartRepository.findByBuyerId("buyer-1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.updateItemQuantity("buyer-1", "product-1", new UpdateCartItemRequest(0));

        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
