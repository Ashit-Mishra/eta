package com.railway.eta.eta;

import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
        // 1. Get live train state
        // ----------------------------------------------------

        TrainState state =
                trainStateService.get(trainNo);

        if (state == null) {

            throw new RuntimeException(
                    "No live state found for train "
                            + trainNo
            );
        }

        if (state.getLastUpdated() == null) {

            throw new RuntimeException(
                    "Train state has no timestamp"
            );
        }


        // ----------------------------------------------------
        // 2. Get train
        // ----------------------------------------------------

        Train train =
                trainRepository
                        .findByTrainNo(trainNo)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: "
                                                + trainNo
                                )
                        );


        // ----------------------------------------------------
        // 3. Get ordered route stations
        // ----------------------------------------------------

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        if (routeStations.isEmpty()) {

            throw new RuntimeException(
                    "No route stations found for train "
                            + trainNo
            );
        }


        // ----------------------------------------------------
        // 4. Find next station
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
        // 5. Check scheduled arrival
        // ----------------------------------------------------

        LocalTime scheduledArrivalTime =
                nextStation.getArrivalTime();

        if (scheduledArrivalTime == null) {
            return 0.0;
        }


        // ----------------------------------------------------
        // 6. Calculate predicted arrival
        // ----------------------------------------------------

        Instant predictedArrival =
                calculatePredictedArrival(state);


        // ----------------------------------------------------
        // 7. Calculate scheduled arrival
        // ----------------------------------------------------

        Instant scheduledArrival =
                createScheduledArrival(
                        state.getLastUpdated(),
                        nextStation
                );


        // ----------------------------------------------------
        // 8. Calculate delay
        //
        // Positive  = delayed
        // Negative  = early
        // Zero      = on time
        // ----------------------------------------------------

        long delaySeconds =
                Duration.between(
                        scheduledArrival,
                        predictedArrival
                ).getSeconds();

        return delaySeconds / 60.0;
    }


    // ========================================================
    // Find next station from route
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
    // Calculate predicted arrival
    // ========================================================

    private Instant calculatePredictedArrival(
            TrainState state
    ) {

        double speedKmh =
                state.getSpeedKmh();

        double distanceKm =
                state.getDistanceToNextStationKm();

        /*
         * If the train isn't moving, we cannot make
         * a meaningful prediction.
         */

        if (speedKmh <= 0) {

            return state.getLastUpdated();
        }


        /*
         * time = distance / speed
         *
         * Example:
         *
         * distance = 1.5 km
         * speed    = 80 km/h
         *
         * time = 1.5 / 80 hours
         */

        double hours =
                distanceKm / speedKmh;


        /*
         * Convert hours to milliseconds.
         */

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
    // Convert schedule time into an Instant
    // ========================================================

    private Instant createScheduledArrival(
            Instant simulationTimestamp,
            RouteStation routeStation
    ) {

        /*
         * The GPS simulator timestamp is in UTC.
         *
         * Example:
         *
         * 2026-09-01T01:50:00Z
         *
         * = 07:20 IST
         *
         * We use the date from the simulation timestamp.
         */

        LocalDate simulationDate =
                simulationTimestamp
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();


        // ----------------------------------------------------
        // Get schedule day
        // ----------------------------------------------------

        int scheduleDay =
                routeStation.getDay() == null
                        ? 1
                        : routeStation.getDay();


        /*
         * Day 1 = simulation date
         * Day 2 = simulation date + 1
         * Day 3 = simulation date + 2
         */

        LocalDate scheduledDate =
                simulationDate.plusDays(
                        scheduleDay - 1L
                );


        // ----------------------------------------------------
        // Convert schedule time from IST to UTC
        // ----------------------------------------------------

        return routeStation
                .getArrivalTime()
                .atDate(scheduledDate)
                .atZone(
                        ZoneOffset.ofHoursMinutes(
                                5,
                                30
                        )
                )
                .toInstant();
    }
}