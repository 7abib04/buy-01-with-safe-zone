package com.buy01.orderservice.event;

import com.buy01.orderservice.model.Order;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String topicName;

    public KafkaOrderEventPublisher(
            KafkaTemplate<String, OrderEvent> kafkaTemplate,
            @Value("${app.kafka.topics.orders}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    @Override
    public void publishCreated(Order order) {
        publish("ORDER_CREATED", order);
    }

    @Override
    public void publishStatusChanged(Order order) {
        publish("ORDER_STATUS_CHANGED", order);
    }

    @Override
    public void publishCancelled(Order order) {
        publish("ORDER_CANCELLED", order);
    }

    private void publish(String eventType, Order order) {
        kafkaTemplate.send(topicName, order.getId(), new OrderEvent(
                eventType,
                order.getId(),
                order.getBuyerId(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getTotalAmount(),
                Instant.now()
        ));
    }
}
