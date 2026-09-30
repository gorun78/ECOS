package com.chinacreator.gzcm.services.apigateway.event;

import com.chinacreator.gzcm.common.event.DomainEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    public DomainEventPublisher(KafkaTemplate<String, DomainEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(DomainEvent event) {
        String topic = KafkaTopics.topicForAggregate(event.getAggregateType());
        String key = event.getAggregateId();
        log.info("Publishing event {} to topic {} with key {}", event.getEventType(), topic, key);
        kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event {} to topic {}: {}", event.getEventType(), topic, ex.getMessage(), ex);
                } else {
                    log.debug("Event {} published to topic {} partition {} offset {}",
                        event.getEventType(), topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }

    public void publish(String eventType, String source, String aggregateType, String aggregateId, Object payload) {
        publish(new DomainEvent(eventType, source, aggregateType, aggregateId, payload));
    }
}
