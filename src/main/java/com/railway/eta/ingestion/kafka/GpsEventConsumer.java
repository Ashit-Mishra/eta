package com.railway.eta.ingestion.kafka;

import com.railway.eta.ingestion.dto.GpsEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class GpsEventConsumer {

    @KafkaListener(
            topics = "train.gps.raw",
            groupId = "railway-eta",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(GpsEvent event) {

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Received GPS event: " + event
        );

        System.out.println(
                "========================================"
        );
    }
}