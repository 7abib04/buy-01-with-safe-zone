package com.buy01.orderservice.controller;

import com.buy01.orderservice.dto.BuyerAnalyticsResponse;
import com.buy01.orderservice.dto.CartResponse;
import com.buy01.orderservice.dto.CheckoutRequest;
import com.buy01.orderservice.dto.OrderResponse;
import com.buy01.orderservice.dto.OrderStatusUpdateRequest;
import com.buy01.orderservice.dto.PageResponse;
import com.buy01.orderservice.dto.SellerAnalyticsResponse;
import com.buy01.orderservice.model.OrderStatus;
import com.buy01.security.AuthenticatedUser;
import com.buy01.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody CheckoutRequest request
    ) {
        return orderService.checkout(user(authentication), authorizationHeader, request);
    }

    @GetMapping
    public PageResponse<OrderResponse> getMyOrders(
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderService.getBuyerOrders(user(authentication), status, q, from, to, page, size);
    }

    @GetMapping("/seller")
    public PageResponse<OrderResponse> getSellerOrders(
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderService.getSellerOrders(user(authentication), status, q, from, to, page, size);
    }

    @GetMapping("/analytics/me")
    public BuyerAnalyticsResponse getBuyerAnalytics(Authentication authentication) {
        return orderService.getBuyerAnalytics(user(authentication));
    }

    @GetMapping("/analytics/seller")
    public SellerAnalyticsResponse getSellerAnalytics(Authentication authentication) {
        return orderService.getSellerAnalytics(user(authentication));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id, Authentication authentication) {
        return orderService.getOrder(user(authentication), id);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable String id,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        return orderService.cancelOrder(user(authentication), id, authorizationHeader);
    }

    @PostMapping("/{id}/redo")
    public CartResponse redoOrder(@PathVariable String id, Authentication authentication) {
        return orderService.redoOrder(user(authentication), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOrder(@PathVariable String id, Authentication authentication) {
        orderService.removeOrder(user(authentication), id);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateOrderStatus(
            @PathVariable String id,
            Authentication authentication,
            @Valid @RequestBody OrderStatusUpdateRequest request
    ) {
        return orderService.updateOrderStatus(user(authentication), id, request);
    }

    private AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
