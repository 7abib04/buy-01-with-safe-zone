package com.buy01.orderservice.repository;

import com.buy01.orderservice.model.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<Order> findByItemsSellerIdOrderByCreatedAtDesc(String sellerId);

    Optional<Order> findByIdAndBuyerId(String id, String buyerId);
}
