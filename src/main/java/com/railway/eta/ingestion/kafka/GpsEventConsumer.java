package com.railway.eta.ingestion.kafka;

import com.railway.eta.eta.TrainState;
import com.railway.eta.eta.TrainStateService;
import com.railway.eta.ingestion.dto.GpsEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class GpsEventConsumer {

    private final TrainStateService trainStateService;

    public GpsEventConsumer(
            TrainStateService trainStateService
    ) {
        this.trainStateService = trainStateService;
    }


    @KafkaListener(
            topics = "train.gps.raw",
            groupId = "railway-eta",
            containerFactory = "kafkaListenerContainerFactory"
    )
        public void consume(GpsEvent event) {

            TrainState state =
                    new TrainState();

            state.setTrainNo(
                    event.trainNo()
            );

            state.setLatitude(
                    event.latitude()
            );

            state.setLongitude(
                    event.longitude()
            );

            state.setSpeedKmh(
                    event.speedKmh()
            );

            state.setLastUpdated(
                    event.timestamp()
            );

            state.setStatus("RUNNING");

            trainStateService.update(state);

            System.out.println(
                    "Live Train State Updated: "
                            + state.getTrainNo()
                            + " | "
                            + state.getLatitude()
                            + ", "
                            + state.getLongitude()
            );
    }
}