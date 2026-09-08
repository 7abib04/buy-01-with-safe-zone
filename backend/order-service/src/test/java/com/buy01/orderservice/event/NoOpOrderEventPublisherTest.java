package com.buy01.orderservice.event;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.buy01.orderservice.model.Order;
import org.junit.jupiter.api.Test;

class NoOpOrderEventPublisherTest {

    private final NoOpOrderEventPublisher publisher = new NoOpOrderEventPublisher();

    @Test
    void publishMethodsDoNothingAndDoNotThrow() {
        Order order = new Order();

        assertThatCode(() -> {
            publisher.publishCreated(order);
            publisher.publishStatusChanged(order);
            publisher.publishCancelled(order);
        }).doesNotThrowAnyException();
    }
}
