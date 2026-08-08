package com.buy01.orderservice.service;

import com.buy01.orderservice.client.ProductServiceClient;
import com.buy01.orderservice.dto.BuyerAnalyticsResponse;
import com.buy01.orderservice.dto.CartItemRequest;
import com.buy01.orderservice.dto.CartResponse;
import com.buy01.orderservice.dto.CategoryStat;
import com.buy01.orderservice.dto.OrderItemResponse;
import com.buy01.orderservice.dto.OrderResponse;
import com.buy01.orderservice.dto.OrderStatusUpdateRequest;
import com.buy01.orderservice.dto.PageResponse;
import com.buy01.orderservice.dto.ProductSnapshotResponse;
import com.buy01.orderservice.dto.ProductStat;
import com.buy01.orderservice.dto.SellerAnalyticsResponse;
import com.buy01.orderservice.dto.ShippingAddressRequest;
import com.buy01.orderservice.dto.ShippingAddressResponse;
import com.buy01.orderservice.dto.CheckoutRequest;
import com.buy01.orderservice.dto.StatusChangeResponse;
import com.buy01.orderservice.event.OrderEventPublisher;
import com.buy01.orderservice.exception.CartEmptyException;
import com.buy01.orderservice.exception.InsufficientStockException;
import com.buy01.orderservice.exception.InvalidOrderStatusTransitionException;
import com.buy01.orderservice.exception.OrderNotFoundException;
import com.buy01.orderservice.model.Cart;
import com.buy01.orderservice.model.CartItem;
import com.buy01.orderservice.model.Order;
import com.buy01.orderservice.model.OrderItem;
import com.buy01.orderservice.model.OrderStatus;
import com.buy01.orderservice.model.PaymentMethod;
import com.buy01.orderservice.model.ShippingAddress;
import com.buy01.orderservice.model.StatusChange;
import com.buy01.orderservice.repository.CartRepository;
import com.buy01.orderservice.repository.OrderRepository;
import com.buy01.orderservice.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final List<OrderStatus> FORWARD_SEQUENCE = List.of(
            OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED
    );
    private static final int ANALYTICS_TOP_N = 5;

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductServiceClient productServiceClient;
    private final OrderEventPublisher orderEventPublisher;
    private final CartService cartService;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            ProductServiceClient productServiceClient,
            OrderEventPublisher orderEventPublisher,
            CartService cartService
    ) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productServiceClient = productServiceClient;
        this.orderEventPublisher = orderEventPublisher;
        this.cartService = cartService;
    }

    public OrderResponse checkout(AuthenticatedUser user, String authorizationHeader, CheckoutRequest request) {
        Cart cart = cartRepository.findByBuyerId(user.userId()).orElseThrow(CartEmptyException::new);
        if (cart.getItems().isEmpty()) {
            throw new CartEmptyException();
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cart.getItems()) {
            ProductSnapshotResponse product = productServiceClient.getProduct(cartItem.getProductId());
            if (cartItem.getQuantity() > product.quantity()) {
                throw new InsufficientStockException(product.name());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.id());
            orderItem.setSellerId(product.sellerId());
            orderItem.setName(product.name());
            orderItem.setImageUrl(cartItem.getImageUrl());
            orderItem.setPrice(product.price());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setCategory(product.category());
            orderItem.setSubtotal(product.price().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            orderItems.add(orderItem);
        }

        decrementStockWithRollback(authorizationHeader, orderItems);

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant now = Instant.now();
        Order order = new Order();
        order.setBuyerId(user.userId());
        order.setBuyerName(user.email());
        order.setItems(orderItems);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setShippingAddress(toShippingAddress(request.shippingAddress()));
        order.setStatusHistory(new ArrayList<>(List.of(new StatusChange(OrderStatus.PENDING, now, user.userId()))));
        order.setHiddenByBuyer(false);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        Order savedOrder = orderRepository.save(order);
        cartService.clearCart(user.userId());
        orderEventPublisher.publishCreated(savedOrder);

        return mapToResponse(savedOrder, null);
    }

    public PageResponse<OrderResponse> getBuyerOrders(
            AuthenticatedUser user, OrderStatus status, String q, Instant from, Instant to, int page, int size
    ) {
        List<Order> orders = orderRepository.findByBuyerIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .filter(order -> !order.isHiddenByBuyer())
                .toList();
        return paginate(filterOrders(orders, status, q, from, to), page, size, null);
    }

    public OrderResponse getOrder(AuthenticatedUser user, String orderId) {
        Order order = findById(orderId);
        boolean isOwner = order.getBuyerId().equals(user.userId());
        boolean isInvolvedSeller = order.getItems().stream().anyMatch(item -> item.getSellerId().equals(user.userId()));
        if (!isOwner && !isInvolvedSeller && !user.isAdmin()) {
            throw new AccessDeniedException("Forbidden");
        }
        String sellerFilter = (!isOwner && !user.isAdmin()) ? user.userId() : null;
        return mapToResponse(order, sellerFilter);
    }

    public OrderResponse cancelOrder(AuthenticatedUser user, String orderId, String authorizationHeader) {
        Order order = findOwnedOrder(orderId, user);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStatusTransitionException(
                    "Only pending or confirmed orders can be cancelled (current status: " + order.getStatus() + ")"
            );
        }

        for (OrderItem item : order.getItems()) {
            try {
                productServiceClient.adjustStock(authorizationHeader, item.getProductId(), item.getQuantity());
            } catch (RuntimeException ignored) {
                // Best-effort restock; the product may have been deleted since the order was placed.
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        order.getStatusHistory().add(new StatusChange(OrderStatus.CANCELLED, order.getUpdatedAt(), user.userId()));

        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishCancelled(savedOrder);
        return mapToResponse(savedOrder, null);
    }

    public CartResponse redoOrder(AuthenticatedUser user, String orderId) {
        Order order = findOwnedOrder(orderId, user);
        CartResponse cart = cartService.getCart(user.userId());
        for (OrderItem item : order.getItems()) {
            try {
                cart = cartService.addItem(user.userId(), new CartItemRequest(item.getProductId(), item.getQuantity()));
            } catch (RuntimeException ignored) {
                // Item may be out of stock, deleted, or already fully present in the cart; skip and continue.
            }
        }
        return cart;
    }

    public void removeOrder(AuthenticatedUser user, String orderId) {
        Order order = findOwnedOrder(orderId, user);
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.DELIVERED) {
            throw new InvalidOrderStatusTransitionException(
                    "Only cancelled or delivered orders can be removed from your history (current status: " + order.getStatus() + ")"
            );
        }
        order.setHiddenByBuyer(true);
        orderRepository.save(order);
    }

    public PageResponse<OrderResponse> getSellerOrders(
            AuthenticatedUser user, OrderStatus status, String q, Instant from, Instant to, int page, int size
    ) {
        List<Order> orders = orderRepository.findByItemsSellerIdOrderByCreatedAtDesc(user.userId());
        return paginate(filterOrders(orders, status, q, from, to), page, size, user.userId());
    }

    public OrderResponse updateOrderStatus(AuthenticatedUser user, String orderId, OrderStatusUpdateRequest request) {
        Order order = findById(orderId);
        boolean isInvolvedSeller = order.getItems().stream().anyMatch(item -> item.getSellerId().equals(user.userId()));
        if (!isInvolvedSeller && !user.isAdmin()) {
            throw new AccessDeniedException("Forbidden");
        }

        OrderStatus target = request.status();
        int currentIndex = FORWARD_SEQUENCE.indexOf(order.getStatus());
        int targetIndex = FORWARD_SEQUENCE.indexOf(target);
        if (currentIndex < 0 || targetIndex != currentIndex + 1) {
            throw new InvalidOrderStatusTransitionException(
                    "Cannot move order from " + order.getStatus() + " to " + target
            );
        }

        order.setStatus(target);
        order.setUpdatedAt(Instant.now());
        order.getStatusHistory().add(new StatusChange(target, order.getUpdatedAt(), user.userId()));

        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishStatusChanged(savedOrder);
        return mapToResponse(savedOrder, user.isAdmin() ? null : user.userId());
    }

    public BuyerAnalyticsResponse getBuyerAnalytics(AuthenticatedUser user) {
        List<Order> orders = orderRepository.findByBuyerIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .toList();

        BigDecimal totalSpent = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderItem> allItems = orders.stream().flatMap(order -> order.getItems().stream()).toList();

        List<ProductStat> mostBought = groupByProduct(allItems).stream()
                .sorted(Comparator.comparingInt(ProductStat::unitsSold).reversed())
                .limit(ANALYTICS_TOP_N)
                .toList();

        List<CategoryStat> topCategories = groupByCategory(allItems);

        return new BuyerAnalyticsResponse(totalSpent, orders.size(), mostBought, topCategories);
    }

    public SellerAnalyticsResponse getSellerAnalytics(AuthenticatedUser user) {
        List<Order> orders = orderRepository.findByItemsSellerIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .toList();

        List<OrderItem> sellerItems = orders.stream()
                .flatMap(order -> sellerItems(order, user.userId()).stream())
                .toList();

        BigDecimal totalRevenue = sellerItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int unitsSold = sellerItems.stream().mapToInt(OrderItem::getQuantity).sum();

        List<ProductStat> bestSelling = groupByProduct(sellerItems).stream()
                .sorted(Comparator.comparingInt(ProductStat::unitsSold).reversed())
                .limit(ANALYTICS_TOP_N)
                .toList();

        return new SellerAnalyticsResponse(totalRevenue, unitsSold, orders.size(), bestSelling);
    }

    private void decrementStockWithRollback(String authorizationHeader, List<OrderItem> orderItems) {
        List<OrderItem> adjusted = new ArrayList<>();
        try {
            for (OrderItem item : orderItems) {
                productServiceClient.adjustStock(authorizationHeader, item.getProductId(), -item.getQuantity());
                adjusted.add(item);
            }
        } catch (RuntimeException exception) {
            for (OrderItem item : adjusted) {
                try {
                    productServiceClient.adjustStock(authorizationHeader, item.getProductId(), item.getQuantity());
                } catch (RuntimeException ignored) {
                    // Best-effort compensation; the original failure is what gets reported.
                }
            }
            throw exception;
        }
    }

    private List<Order> filterOrders(List<Order> orders, OrderStatus status, String q, Instant from, Instant to) {
        String normalizedQuery = q == null ? null : q.trim().toLowerCase();
        return orders.stream()
                .filter(order -> status == null || order.getStatus() == status)
                .filter(order -> from == null || !order.getCreatedAt().isBefore(from))
                .filter(order -> to == null || !order.getCreatedAt().isAfter(to))
                .filter(order -> normalizedQuery == null || normalizedQuery.isBlank()
                        || order.getId().toLowerCase().contains(normalizedQuery)
                        || order.getItems().stream().anyMatch(item -> item.getName() != null
                                && item.getName().toLowerCase().contains(normalizedQuery)))
                .toList();
    }

    private PageResponse<OrderResponse> paginate(List<Order> orders, int page, int size, String sellerFilter) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        int fromIndex = Math.min(safePage * safeSize, orders.size());
        int toIndex = Math.min(fromIndex + safeSize, orders.size());

        List<OrderResponse> content = orders.subList(fromIndex, toIndex).stream()
                .map(order -> mapToResponse(order, sellerFilter))
                .toList();

        return PageResponse.of(content, safePage, safeSize, orders.size());
    }

    private Order findById(String id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private Order findOwnedOrder(String id, AuthenticatedUser user) {
        return orderRepository.findByIdAndBuyerId(id, user.userId())
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private List<OrderItem> sellerItems(Order order, String sellerId) {
        return order.getItems().stream().filter(item -> item.getSellerId().equals(sellerId)).toList();
    }

    private List<ProductStat> groupByProduct(List<OrderItem> items) {
        Map<String, ProductStat> byProduct = new LinkedHashMap<>();
        for (OrderItem item : items) {
            byProduct.merge(
                    item.getProductId(),
                    new ProductStat(item.getProductId(), item.getName(), item.getQuantity(), item.getSubtotal()),
                    (existing, addition) -> new ProductStat(
                            existing.productId(),
                            existing.name(),
                            existing.unitsSold() + addition.unitsSold(),
                            existing.revenue().add(addition.revenue())
                    )
            );
        }
        return new ArrayList<>(byProduct.values());
    }

    private List<CategoryStat> groupByCategory(List<OrderItem> items) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (OrderItem item : items) {
            String category = item.getCategory() == null || item.getCategory().isBlank() ? "UNCATEGORIZED" : item.getCategory();
            byCategory.merge(category, item.getSubtotal(), BigDecimal::add);
        }
        return byCategory.entrySet().stream()
                .map(entry -> new CategoryStat(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryStat::amount).reversed())
                .toList();
    }

    private ShippingAddress toShippingAddress(ShippingAddressRequest request) {
        ShippingAddress address = new ShippingAddress();
        address.setFullName(request.fullName().trim());
        address.setPhone(request.phone().trim());
        address.setLine1(request.line1().trim());
        address.setCity(request.city().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountry(request.country().trim());
        return address;
    }

    private OrderResponse mapToResponse(Order order, String sellerFilter) {
        List<OrderItem> sourceItems = sellerFilter == null ? order.getItems() : sellerItems(order, sellerFilter);

        List<OrderItemResponse> items = sourceItems.stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getSellerId(),
                        item.getSellerName(),
                        item.getName(),
                        item.getImageUrl(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getCategory(),
                        item.getSubtotal()
                ))
                .toList();

        BigDecimal scopedTotal = sellerFilter == null
                ? order.getTotalAmount()
                : items.stream().map(OrderItemResponse::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        ShippingAddressResponse shippingAddress = order.getShippingAddress() == null ? null : new ShippingAddressResponse(
                order.getShippingAddress().getFullName(),
                order.getShippingAddress().getPhone(),
                order.getShippingAddress().getLine1(),
                order.getShippingAddress().getCity(),
                order.getShippingAddress().getPostalCode(),
                order.getShippingAddress().getCountry()
        );

        List<StatusChangeResponse> statusHistory = order.getStatusHistory().stream()
                .map(change -> new StatusChangeResponse(change.getStatus(), change.getChangedAt(), change.getChangedBy()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getBuyerId(),
                order.getBuyerName(),
                items,
                scopedTotal,
                order.getStatus(),
                order.getPaymentMethod(),
                shippingAddress,
                statusHistory,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
