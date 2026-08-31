package com.railway.eta.eta;

import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DelayCalculationService {

    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final TrainStateService trainStateService;

    public DelayCalculationService(
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository,
            TrainStateService trainStateService
    ) {
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
        this.trainStateService = trainStateService;
    }

    @Transactional(readOnly = true)
    public double calculateDelayMinutes(String trainNo) {

        // ----------------------------------------------------
        // Get current live train state
        // ----------------------------------------------------

        TrainState state =
                trainStateService.get(trainNo);

        if (state == null) {
            throw new RuntimeException(
                    "No live state found for train " + trainNo
            );
        }

        if (state.getLastUpdated() == null) {
            throw new RuntimeException(
                    "Train state has no timestamp"
            );
        }


        // ----------------------------------------------------
        // Get train
        // ----------------------------------------------------

        Train train =
                trainRepository
                        .findByTrainNo(trainNo)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: " + trainNo
                                )
                        );


        // ----------------------------------------------------
        // Get ordered route stations
        // ----------------------------------------------------

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        if (routeStations.isEmpty()) {
            throw new RuntimeException(
                    "No route stations found for train " + trainNo
            );
        }


        // ----------------------------------------------------
        // Find next station
        // ----------------------------------------------------

        RouteStation nextStation =
                findNextStation(
                        state.getNextStation(),
                        routeStations
                );

        if (nextStation == null) {
            return 0.0;
        }


        // ----------------------------------------------------
        // We need scheduled arrival
        // ----------------------------------------------------

        LocalTime scheduledArrivalTime =
                nextStation.getArrivalTime();

        if (scheduledArrivalTime == null) {
            return 0.0;
        }


        // ----------------------------------------------------
        // Calculate predicted arrival
        // ----------------------------------------------------

        Instant predictedArrival =
                calculatePredictedArrival(state);


        // ----------------------------------------------------
        // Convert schedule time into an Instant
        //
        // We use the SAME simulation date as the GPS event.
        // ----------------------------------------------------

        Instant scheduledArrival =
                createScheduledArrival(
                        state.getLastUpdated(),
                        scheduledArrivalTime
                );


        // ----------------------------------------------------
        // Delay
        //
        // positive = late
        // negative = early
        // ----------------------------------------------------

        long delaySeconds =
                Duration.between(
                        scheduledArrival,
                        predictedArrival
                ).getSeconds();

        return delaySeconds / 60.0;
    }


    // ========================================================
    // Find next RouteStation
    // ========================================================

    private RouteStation findNextStation(
            String stationCode,
            List<RouteStation> routeStations
    ) {

        if (stationCode == null) {
            return null;
        }

        for (RouteStation routeStation : routeStations) {

            if (
                    routeStation
                            .getStation()
                            .getCode()
                            .equals(stationCode)
            ) {

                return routeStation;
            }
        }

        return null;
    }


    // ========================================================
    // Predicted arrival
    // ========================================================

    private Instant calculatePredictedArrival(
            TrainState state
    ) {

        double speedKmh =
                state.getSpeedKmh();

        double distanceKm =
                state.getDistanceToNextStationKm();

        if (speedKmh <= 0) {

            return state.getLastUpdated();
        }


        // distance / speed = hours

        double hours =
                distanceKm / speedKmh;


        // Convert hours to milliseconds

        long milliseconds =
                (long) (
                        hours
                                * 60
                                * 60
                                * 1000
                );


        return state
                .getLastUpdated()
                .plusMillis(milliseconds);
    }


    // ========================================================
    // Create scheduled arrival Instant
    // ========================================================

    private Instant createScheduledArrival(
            Instant simulationTimestamp,
            LocalTime scheduledTime
    ) {

        /*
         * Convert the GPS simulation timestamp into the
         * simulation day's date.
         *
         * Example:
         *
         * GPS timestamp:
         * 2026-09-01T01:50:05Z
         *
         * Scheduled arrival:
         * 07:35 IST
         *
         * 07:35 IST = 02:05 UTC
         */

        var simulationDate =
                simulationTimestamp
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();


        return scheduledTime
                .atDate(simulationDate)
                .atZone(
                        ZoneOffset.ofHoursMinutes(
                                5,
                                30
                        )
                )
                .toInstant();
    }
}