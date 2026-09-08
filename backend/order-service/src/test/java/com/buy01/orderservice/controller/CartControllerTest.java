package com.buy01.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buy01.orderservice.dto.CartItemRequest;
import com.buy01.orderservice.dto.CartResponse;
import com.buy01.orderservice.dto.UpdateCartItemRequest;
import com.buy01.security.AuthenticatedUser;
import com.buy01.orderservice.service.CartService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class CartControllerTest {

    private final CartService cartService = mock(CartService.class);
    private final CartController controller = new CartController(cartService);
    private final Authentication authentication = mock(Authentication.class);
    private final AuthenticatedUser user = new AuthenticatedUser("buyer-1", "buyer@example.com", "CLIENT");
    private final CartResponse emptyCart = new CartResponse(List.of(), 0, BigDecimal.ZERO);

    @BeforeEach
    void setUp() {
        when(authentication.getPrincipal()).thenReturn(user);
    }

    @Test
    void getCartDelegatesToCartService() {
        when(cartService.getCart("buyer-1")).thenReturn(emptyCart);

        assertThat(controller.getCart(authentication)).isSameAs(emptyCart);
    }

    @Test
    void addItemDelegatesToCartService() {
        CartItemRequest request = new CartItemRequest("product-1", 2);
        when(cartService.addItem("buyer-1", request)).thenReturn(emptyCart);

        assertThat(controller.addItem(authentication, request)).isSameAs(emptyCart);
    }

    @Test
    void updateItemDelegatesToCartService() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(3);
        when(cartService.updateItemQuantity("buyer-1", "product-1", request)).thenReturn(emptyCart);

        assertThat(controller.updateItem("product-1", authentication, request)).isSameAs(emptyCart);
    }

    @Test
    void removeItemDelegatesToCartService() {
        when(cartService.removeItem("buyer-1", "product-1")).thenReturn(emptyCart);

        assertThat(controller.removeItem("product-1", authentication)).isSameAs(emptyCart);
    }

    @Test
    void clearCartDelegatesToCartService() {
        controller.clearCart(authentication);

        verify(cartService).clearCart("buyer-1");
    }
}
