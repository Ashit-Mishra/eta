package com.railway.eta.history;

import com.railway.eta.ingestion.dto.GpsEvent;
import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

@Service
public class StationArrivalHistoryService {

    private final StationArrivalHistoryRepository repository;
    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;

    public StationArrivalHistoryService(
            StationArrivalHistoryRepository repository,
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository
    ) {
        this.repository = repository;
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
    }

    @Transactional
    public void processArrival(GpsEvent event) {

        String trainNo = event.trainNo();

        Train train =
                trainRepository.findByTrainNo(trainNo)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: " + trainNo
                                )
                        );

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        if (routeStations.isEmpty()) {
            return;
        }

        String stationCode =
                event.currentStation();

        if (stationCode == null) {
            return;
        }

        RouteStation routeStation = null;

        for (RouteStation rs : routeStations) {

            if (rs.getStation().getCode().equals(stationCode)) {
                routeStation = rs;
                break;
            }
        }

        if (routeStation == null) {
            return;
        }

        // ----------------------------------------------------
        // Prevent duplicate arrival records
        // ----------------------------------------------------

        Long runId = event.runId();

        if (repository.existsByRunIdAndStationCode(
                runId,
                stationCode
        )) {
            return;
        }

        // ----------------------------------------------------
        // Actual arrival
        // ----------------------------------------------------

        Instant actualArrival =
                event.timestamp();

        // ----------------------------------------------------
        // Scheduled arrival
        // ----------------------------------------------------

        Instant scheduledArrival = null;

        if (routeStation.getArrivalTime() != null) {

            LocalDate simulationDate =
                    event.timestamp()
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate();

            int scheduleDay =
                    routeStation.getDay() == null
                            ? 1
                            : routeStation.getDay();

            LocalDate scheduledDate =
                    simulationDate.plusDays(
                            scheduleDay - 1L
                    );

            scheduledArrival =
                    routeStation
                            .getArrivalTime()
                            .atDate(scheduledDate)
                            .atZone(
                                    ZoneId.of("Asia/Kolkata")
                            )
                            .toInstant();
        }

        // ----------------------------------------------------
        // Delay
        // ----------------------------------------------------

        double delayMinutes = 0.0;

        if (scheduledArrival != null) {

            long delaySeconds =
                    java.time.Duration.between(
                            scheduledArrival,
                            actualArrival
                    ).getSeconds();

            delayMinutes =
                    delaySeconds / 60.0;
        }

        // ----------------------------------------------------
        // Save
        // ----------------------------------------------------

        StationArrivalHistory history =
                new StationArrivalHistory();

        history.setTrainNo(
                trainNo
        );

        history.setRunId(
                runId
        );

        history.setStationCode(
                stationCode
        );

        history.setScheduledArrival(
                scheduledArrival
        );

        history.setActualArrival(
                actualArrival
        );

        history.setDelayMinutes(
                delayMinutes
        );

        repository.save(history);
    }
}