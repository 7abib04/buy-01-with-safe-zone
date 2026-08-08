package com.buy01.orderservice.event;

import com.buy01.orderservice.model.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpOrderEventPublisher implements OrderEventPublisher {

    @Override
    public void publishCreated(Order order) {
    }

    @Override
    public void publishStatusChanged(Order order) {
    }

    @Override
    public void publishCancelled(Order order) {
    }
}
