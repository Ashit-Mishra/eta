package com.railway.eta.ingestion.kafka;

import com.railway.eta.eta.TrainState;
import com.railway.eta.eta.TrainStateService;
import com.railway.eta.history.GpsHistory;
import com.railway.eta.history.GpsHistoryRepository;
import com.railway.eta.ingestion.dto.GpsEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class GpsEventConsumer {

    private final TrainStateService trainStateService;

    private final GpsHistoryRepository gpsHistoryRepository;


    public GpsEventConsumer(
            TrainStateService trainStateService,
            GpsHistoryRepository gpsHistoryRepository
    ) {
        this.trainStateService =
                trainStateService;

        this.gpsHistoryRepository =
                gpsHistoryRepository;
    }


    @KafkaListener(
            topics = "train.gps.raw",
            groupId = "railway-eta",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(GpsEvent event) {

        // ====================================================
        // 1. UPDATE LIVE TRAIN STATE
        // ====================================================

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

        state.setStatus(
                "RUNNING"
        );

        trainStateService.update(
                state
        );


        // ====================================================
        // 2. SAVE GPS HISTORY
        // ====================================================

        GpsHistory history =
                new GpsHistory();

        history.setTrainNo(
                event.trainNo()
        );

        history.setLatitude(
                event.latitude()
        );

        history.setLongitude(
                event.longitude()
        );

        history.setSpeedKmh(
                event.speedKmh()
        );

        history.setTimestamp(
                event.timestamp()
        );

        gpsHistoryRepository.save(
                history
        );


        // ====================================================
        // 3. LOG
        // ====================================================

        System.out.println(
                "Live Train State Updated: "
                        + state.getTrainNo()
                        + " | "
                        + state.getLatitude()
                        + ", "
                        + state.getLongitude()
        );

        System.out.println(
                "GPS History Saved: "
                        + event.trainNo()
                        + " | speed="
                        + event.speedKmh()
                        + " km/h"
        );
    }
}