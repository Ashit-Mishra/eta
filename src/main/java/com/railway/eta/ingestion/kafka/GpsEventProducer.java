package com.railway.eta.ingestion.kafka;

import com.railway.eta.ingestion.dto.GpsEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class GpsEventProducer {

    private static final String TOPIC = "train.gps.raw";

    private final KafkaTemplate<String, GpsEvent> kafkaTemplate;

    public GpsEventProducer(KafkaTemplate<String, GpsEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(GpsEvent event) {

        kafkaTemplate.send(
                TOPIC,
                event.trainNo(),
                event
        );
    }
}