package com.buy01.orderservice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.buy01.orderservice.model.Order;
import com.buy01.orderservice.model.OrderStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaOrderEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaOrderEventPublisher publisher = new KafkaOrderEventPublisher(kafkaTemplate, "orders-topic");

    private Order sampleOrder() {
        Order order = new Order();
        order.setId("order-1");
        order.setBuyerId("buyer-1");
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("50.00"));
        return order;
    }

    @Test
    void publishCreatedSendsOrderCreatedEvent() {
        publisher.publishCreated(sampleOrder());

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("orders-topic"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("ORDER_CREATED");
        assertThat(captor.getValue().orderId()).isEqualTo("order-1");
        assertThat(captor.getValue().buyerId()).isEqualTo("buyer-1");
        assertThat(captor.getValue().status()).isEqualTo("PENDING");
        assertThat(captor.getValue().totalAmount()).isEqualByComparingTo("50.00");
        assertThat(captor.getValue().occurredAt()).isNotNull();
    }

    @Test
    void publishStatusChangedSendsOrderStatusChangedEvent() {
        publisher.publishStatusChanged(sampleOrder());

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("orders-topic"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("ORDER_STATUS_CHANGED");
    }

    @Test
    void publishCancelledSendsOrderCancelledEvent() {
        publisher.publishCancelled(sampleOrder());

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("orders-topic"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("ORDER_CANCELLED");
    }

    @Test
    void publishHandlesNullStatus() {
        Order order = sampleOrder();
        order.setStatus(null);

        publisher.publishCreated(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("orders-topic"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().status()).isNull();
    }
}
