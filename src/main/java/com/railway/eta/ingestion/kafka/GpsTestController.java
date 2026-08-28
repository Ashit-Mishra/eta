package com.railway.eta.ingestion.kafka;

import com.railway.eta.ingestion.dto.GpsEvent;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/test")
public class GpsTestController {

    private final GpsEventProducer producer;

    public GpsTestController(GpsEventProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/gps")
    public String sendGps() {

        GpsEvent event = new GpsEvent(
                "12001",
                28.6500,
                77.3200,
                82.5,
                Instant.now()
        );

        producer.send(event);

        return "GPS event sent";
    }
}