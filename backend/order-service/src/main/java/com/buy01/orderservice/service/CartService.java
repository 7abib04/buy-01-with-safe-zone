package com.buy01.orderservice.service;

import com.buy01.orderservice.client.ProductServiceClient;
import com.buy01.orderservice.dto.CartItemRequest;
import com.buy01.orderservice.dto.CartItemResponse;
import com.buy01.orderservice.dto.CartResponse;
import com.buy01.orderservice.dto.ProductSnapshotResponse;
import com.buy01.orderservice.dto.UpdateCartItemRequest;
import com.buy01.orderservice.exception.InsufficientStockException;
import com.buy01.orderservice.exception.ProductNotFoundException;
import com.buy01.orderservice.model.Cart;
import com.buy01.orderservice.model.CartItem;
import com.buy01.orderservice.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductServiceClient productServiceClient;

    public CartService(CartRepository cartRepository, ProductServiceClient productServiceClient) {
        this.cartRepository = cartRepository;
        this.productServiceClient = productServiceClient;
    }

    public CartResponse getCart(String buyerId) {
        return mapToResponse(loadCart(buyerId));
    }

    public CartResponse addItem(String buyerId, CartItemRequest request) {
        Cart cart = loadCart(buyerId);
        ProductSnapshotResponse product = productServiceClient.getProduct(request.productId());

        Optional<CartItem> existing = findItem(cart, request.productId());
        int desiredQuantity = existing.map(CartItem::getQuantity).orElse(0) + request.quantity();
        if (desiredQuantity > product.quantity()) {
            throw new InsufficientStockException(product.name());
        }

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(desiredQuantity);
            item.setPrice(product.price());
            item.setName(product.name());
            item.setImageUrl(firstImage(product.imageUrls()));
        } else {
            cart.getItems().add(new CartItem(
                    product.id(),
                    product.sellerId(),
                    product.name(),
                    firstImage(product.imageUrls()),
                    product.price(),
                    request.quantity()
            ));
        }

        return mapToResponse(persist(cart));
    }

    public CartResponse updateItemQuantity(String buyerId, String productId, UpdateCartItemRequest request) {
        Cart cart = loadCart(buyerId);

        if (request.quantity() == 0) {
            cart.getItems().removeIf(item -> item.getProductId().equals(productId));
            return mapToResponse(persist(cart));
        }

        CartItem item = findItem(cart, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        ProductSnapshotResponse product = productServiceClient.getProduct(productId);
        if (request.quantity() > product.quantity()) {
            throw new InsufficientStockException(product.name());
        }
        item.setQuantity(request.quantity());
        item.setPrice(product.price());

        return mapToResponse(persist(cart));
    }

    public CartResponse removeItem(String buyerId, String productId) {
        Cart cart = loadCart(buyerId);
        cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        return mapToResponse(persist(cart));
    }

    public CartResponse clearCart(String buyerId) {
        Cart cart = loadCart(buyerId);
        cart.getItems().clear();
        return mapToResponse(persist(cart));
    }

    Cart loadCart(String buyerId) {
        return cartRepository.findByBuyerId(buyerId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setBuyerId(buyerId);
            return cart;
        });
    }

    private Cart persist(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        return cartRepository.save(cart);
    }

    private Optional<CartItem> findItem(Cart cart, String productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();
    }

    private String firstImage(List<String> imageUrls) {
        return imageUrls == null || imageUrls.isEmpty() ? null : imageUrls.get(0);
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartItemResponse(
                        item.getProductId(),
                        item.getSellerId(),
                        item.getName(),
                        item.getImageUrl(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                ))
                .toList();

        int totalItems = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal totalAmount = items.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(items, totalItems, totalAmount);
    }
}
