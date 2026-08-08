package com.buy01.orderservice.event;

import com.buy01.orderservice.model.Order;

public interface OrderEventPublisher {

    void publishCreated(Order order);

    void publishStatusChanged(Order order);

    void publishCancelled(Order order);
}
