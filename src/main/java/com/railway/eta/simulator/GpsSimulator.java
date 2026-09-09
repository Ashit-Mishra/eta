package com.railway.eta.simulator;

import com.railway.eta.history.TrainRun;
import com.railway.eta.history.TrainRunService;
import com.railway.eta.ingestion.dto.GpsEvent;
import com.railway.eta.ingestion.kafka.GpsEventProducer;
import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GpsSimulator {

    // ========================================================
    // CONFIGURATION
    // ========================================================

    /*
     * Start with 10 trains.
     *
     * Later we can increase this to:
     *
     * 10
     * 100
     * 1000
     * 5208
     *
     * without changing the simulation architecture.
     */
    private static final int MAX_SIMULATED_TRAINS = 100;

    /*
     * Generate one GPS event every 2 seconds.
     */
    private static final long TICK_MILLIS = 2000;

    // ========================================================
    // SPEED
    // ========================================================
    private static final double SIMULATION_SPEED_MULTIPLIER = 1.0;

    /*
     * Normal speed is calculated from the timetable for each segment.
     * Delay speeds are percentages of that scheduled speed.
     */
    private static final double SPEED_DELAY_FACTOR = 0.70;
    private static final double WEATHER_DELAY_FACTOR = 0.60;
    private static final double SIGNAL_DELAY_FACTOR = 0.0;

    // ========================================================
    // DEPENDENCIES
    // ========================================================

    private final GpsEventProducer producer;

    private final GpsRouteService gpsRouteService;

    private final TrainRepository trainRepository;

    private final RouteStationRepository routeStationRepository;

    private final TrainRunService trainRunService;

    // ========================================================
    // ALL ACTIVE TRAIN SIMULATIONS
    // ========================================================

    /*
     * One independent state object per train.
     *
     * Example:
     *
     * 12031 -> TrainSimulationState
     * 12032 -> TrainSimulationState
     * 12033 -> TrainSimulationState
     */
    private final Map<String, TrainSimulationState> simulations =
            new ConcurrentHashMap<>();

    /*
     * Normal operating speed for each train segment, derived from
     * the timetable instead of using one fixed 80 km/h value.
     */
    private final Map<String, List<Double>> scheduledSpeedsKmh =
            new ConcurrentHashMap<>();

    // ========================================================
    // INITIALIZATION FLAG
    // ========================================================

    private volatile boolean initialized = false;

    // ========================================================
    // CONSTRUCTOR
    // ========================================================

    public GpsSimulator(
            GpsEventProducer producer,
            GpsRouteService gpsRouteService,
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository,
            TrainRunService trainRunService
    ) {
        this.producer = producer;
        this.gpsRouteService = gpsRouteService;
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
        this.trainRunService = trainRunService;
    }

    // ========================================================
    // MAIN SIMULATION LOOP
    // ========================================================

    @Scheduled(fixedRate = TICK_MILLIS)
    public void generateGpsEvents() {

        // ----------------------------------------------------
        // Initialize all selected trains once
        // ----------------------------------------------------

        if (!initialized) {
            initializeAllTrains();
        }

        if (simulations.isEmpty()) {
            return;
        }

        // ----------------------------------------------------
        // Update every train
        // ----------------------------------------------------

        for (TrainSimulationState state : simulations.values()) {

            if (state.isCompleted()) {
                continue;
            }

            try {
                updateTrain(state);
            } catch (Exception e) {

                System.err.println(
                        "Error processing train "
                                + state.getTrainNo()
                                + ": "
                                + e.getMessage()
                );

                e.printStackTrace();
            }
        }
    }

    // ========================================================
    // UPDATE ONE TRAIN
    // ========================================================

    private void updateTrain(
            TrainSimulationState state
    ) {

        List<SimulatedSegment> segments =
                state.getSegments();

        if (segments == null || segments.isEmpty()) {

            System.err.println(
                    "No route segments found for train "
                            + state.getTrainNo()
            );

            state.setCompleted(true);

            return;
        }

        // ----------------------------------------------------
        // Calculate elapsed real time
        // ----------------------------------------------------

        long currentTime =
                System.currentTimeMillis();

        double elapsedSeconds =
                (currentTime - state.getLastRealTime())
                        / 1000.0
                        * SIMULATION_SPEED_MULTIPLIER;

        state.setLastRealTime(currentTime);

        // ----------------------------------------------------
        // Advance simulation clock
        // ----------------------------------------------------

        Instant newSimulationTime =
                state.getSimulationTime()
                        .plusMillis(
                                (long) (elapsedSeconds * 1000)
                        );

        state.setSimulationTime(newSimulationTime);

        // ----------------------------------------------------
        // Update delay scenario
        // ----------------------------------------------------

        updateDelayState(state);

        // ----------------------------------------------------
        // Wait at a station until its scheduled departure time.
        //
        // This prevents the simulator from immediately starting
        // the next segment after reaching a station.
        // ----------------------------------------------------

        if (isWaitingForScheduledDeparture(state)) {
            state.setSpeedKmh(0.0);
            sendStationWaitingEvent(state);
            return;
        }

        // ----------------------------------------------------
        // Calculate distance travelled
        //
        // distance = speed × time / 3600
        // ----------------------------------------------------

        double distanceThisTick =
                state.getSpeedKmh()
                        * elapsedSeconds
                        / 3600.0;

        // ----------------------------------------------------
        // HARD ARRIVAL RESTRICTION
        //
        // Never allow the train to reach the next station before
        // its scheduled arrival time. If the current speed would
        // make it arrive early, cap this tick's movement so that
        // the train reaches the station no earlier than scheduled.
        // ----------------------------------------------------

        Instant scheduledArrival =
                getScheduledArrivalTime(
                        state,
                        state.getCurrentSegment()
                );

        if (
                scheduledArrival != null
                        &&
                        state.getSimulationTime()
                                .isBefore(scheduledArrival)
        ) {
            double secondsUntilArrival =
                    java.time.Duration.between(
                            state.getSimulationTime(),
                            scheduledArrival
                    ).toMillis() / 1000.0;

            double remainingDistance =
                    segments
                            .get(state.getCurrentSegment())
                            .distanceKm()
                            - state.getDistanceTravelledKm();

            if (
                    secondsUntilArrival > 0
                            &&
                            remainingDistance > 0
            ) {
                double maximumAllowedDistance =
                        remainingDistance
                                * elapsedSeconds
                                / secondsUntilArrival;

                distanceThisTick =
                        Math.min(
                                distanceThisTick,
                                maximumAllowedDistance
                        );
            }
        }

        state.setDistanceTravelledKm(
                state.getDistanceTravelledKm()
                        + distanceThisTick
        );

        // ----------------------------------------------------
        // Check whether station/segment reached
        // ----------------------------------------------------

        while (
                state.getCurrentSegment()
                        < segments.size()
                        &&
                        state.getDistanceTravelledKm()
                                >=
                                segments
                                        .get(state.getCurrentSegment())
                                        .distanceKm()
        ) {

            double segmentDistance =
                    segments
                            .get(state.getCurrentSegment())
                            .distanceKm();

            state.setDistanceTravelledKm(
                    state.getDistanceTravelledKm()
                            - segmentDistance
            );

            state.setCurrentSegment(
                    state.getCurrentSegment() + 1
            );

            // ------------------------------------------------
            // Destination reached
            // ------------------------------------------------

            if (
                    state.getCurrentSegment()
                            >= segments.size()
            ) {

                completeTrain(state);

                return;
            }
        }

        // ----------------------------------------------------
        // Get current segment
        // ----------------------------------------------------

        SimulatedSegment segment =
                segments.get(
                        state.getCurrentSegment()
                );

        SimulatedStation from =
                segment.from();

        SimulatedStation to =
                segment.to();

        // ----------------------------------------------------
        // Calculate progress
        // ----------------------------------------------------

        double progress = 0.0;

        if (segment.distanceKm() > 0) {

            progress =
                    state.getDistanceTravelledKm()
                            / segment.distanceKm();
        }

        progress =
                Math.max(
                        0.0,
                        Math.min(1.0, progress)
                );

        // ----------------------------------------------------
        // Get GPS position from REAL LineString
        // ----------------------------------------------------

        SimulatedPoint currentPoint =
                getPointAlongGeometry(
                        segment.geometryPoints(),
                        state.getDistanceTravelledKm()
                );

        double latitude =
                currentPoint.latitude();

        double longitude =
                currentPoint.longitude();

        // ----------------------------------------------------
        // Create GPS event
        // ----------------------------------------------------

        GpsEvent event =
                new GpsEvent(
                        state.getTrainNo(),
                        state.getRunId(),
                        latitude,
                        longitude,
                        state.getSpeedKmh(),
                        state.getSimulationTime(),
                        from.code(),
                        to.code(),
                        state.getCurrentDelayType(),
                        progress
                );

        // ----------------------------------------------------
        // Send GPS event to Kafka
        // ----------------------------------------------------

        producer.send(event);

        // ----------------------------------------------------
        // Console logging
        // ----------------------------------------------------

        System.out.printf(
                "[GPS] Train=%s | From=%s | To=%s | "
                        + "Speed=%.1f km/h | Delay=%s | "
                        + "GPS=%.6f,%.6f%n",

                state.getTrainNo(),
                from.code(),
                to.code(),
                state.getSpeedKmh(),
                state.getCurrentDelayType(),
                latitude,
                longitude
        );
    }

    private Instant getScheduledArrivalTime(
            TrainSimulationState state,
            int segmentIndex
    ) {
        if (segmentIndex < 0) {
            return null;
        }

        Long routeId =
                findRouteId(state.getTrainNo());

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                routeId
                        );

        int arrivalStationIndex =
                segmentIndex + 1;

        if (
                arrivalStationIndex < 0
                        ||
                        arrivalStationIndex >= routeStations.size()
        ) {
            return null;
        }

        RouteStation station =
                routeStations.get(arrivalStationIndex);

        LocalTime arrivalTime =
                station.getArrivalTime();

        if (arrivalTime == null) {
            return null;
        }

        int stationDay =
                station.getDay() == null
                        ? 1
                        : station.getDay();

        LocalDate scheduledDate =
                LocalDate.now()
                        .plusDays(stationDay - 1L);

        ZoneId zone =
                ZoneId.systemDefault();

        return ZonedDateTime.of(
                scheduledDate,
                arrivalTime,
                zone
        ).toInstant();
    }

    private boolean isWaitingForScheduledDeparture(
            TrainSimulationState state
    ) {
        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                findRouteId(state.getTrainNo())
                        );

        int currentSegment = state.getCurrentSegment();

        // currentSegment points to the segment leaving the current station.
        // Therefore the current station is at the same index.
        if (currentSegment <= 0 || currentSegment >= routeStations.size()) {
            return false;
        }

        RouteStation currentStation =
                routeStations.get(currentSegment);

        LocalTime departureTime =
                currentStation.getDepartureTime();

        if (departureTime == null) {
            return false;
        }

        int stationDay =
                currentStation.getDay() == null
                        ? 1
                        : currentStation.getDay();

        LocalDate scheduledDate =
                LocalDate.now().plusDays(stationDay - 1L);

        ZoneId zone = ZoneId.systemDefault();

        Instant scheduledDeparture =
                ZonedDateTime.of(
                        scheduledDate,
                        departureTime,
                        zone
                ).toInstant();

        return state.getSimulationTime()
                .isBefore(scheduledDeparture);
    }

    private Long findRouteId(String trainNo) {
        return trainRepository
                .findByTrainNo(trainNo)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Train not found: " + trainNo
                        )
                )
                .getRoute()
                .getId();
    }

    private void sendStationWaitingEvent(
            TrainSimulationState state
    ) {
        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                findRouteId(state.getTrainNo())
                        );

        int currentSegment = state.getCurrentSegment();

        if (currentSegment < 0 ||
                currentSegment >= routeStations.size()) {
            return;
        }

        RouteStation station =
                routeStations.get(currentSegment);

        var stationData = station.getStation();

        GpsEvent event =
                new GpsEvent(
                        state.getTrainNo(),
                        state.getRunId(),
                        stationData.getLatitude(),
                        stationData.getLongitude(),
                        0.0,
                        state.getSimulationTime(),
                        stationData.getCode(),
                        currentSegment + 1 < routeStations.size()
                                ? routeStations
                                .get(currentSegment + 1)
                                .getStation()
                                .getCode()
                                : stationData.getCode(),
                        state.getCurrentDelayType(),
                        1.0
                );

        producer.send(event);
    }

    // ========================================================
    // COMPLETE TRAIN
    // ========================================================

    private void completeTrain(
            TrainSimulationState state
    ) {

        if (state.isCompleted()) {
            return;
        }

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Train "
                        + state.getTrainNo()
                        + " reached its destination."
        );

        System.out.println(
                "Simulation time: "
                        + state.getSimulationTime()
        );

        System.out.println(
                "========================================"
        );

        // ----------------------------------------------------
        // Complete TrainRun
        // ----------------------------------------------------

        if (state.getRunId() != null) {

            /*
             * Keep the current behaviour for now.
             *
             * We will calculate the actual final delay
             * from schedule-vs-actual arrival in a later step.
             */
            double finalDelayMinutes = 0.0;

            trainRunService.completeRun(
                    state.getRunId(),
                    state.getSimulationTime(),
                    finalDelayMinutes
            );

            state.setRunId(null);
        }

        state.setCompleted(true);
    }

    // ========================================================
    // DELAY CONTROLLER
    // ========================================================

    private void updateDelayState(
            TrainSimulationState state
    ) {

        if (state.getSimulationStartTime() == null) {
            return;
        }

        long elapsedSimulationSeconds =
                java.time.Duration
                        .between(
                                state.getSimulationStartTime(),
                                state.getSimulationTime()
                        )
                        .getSeconds();

        /*
         * Delay distribution target:
         *
         * NONE      = 50%
         * WEATHER   = 25%
         * SIGNAL    = 12.5%
         * SPEED     = 12.5%
         *
         * One 800-second cycle is divided using exactly these
         * proportions. The cycle repeats for the complete run.
         *
         * The simulator runs at 10x speed, so the 2-second real-time
         * tick advances roughly 20 simulated seconds. The phase lengths
         * are deliberately much larger than one tick so that all delay
         * types produce multiple GPS observations.
         */
        long cycleSeconds = elapsedSimulationSeconds % 800;

        TrainSimulationState.DelayType newDelayType;

        // 0 - 400 seconds = 50% NONE
        if (cycleSeconds < 400) {
            newDelayType = TrainSimulationState.DelayType.NONE;
        }

        // 400 - 600 seconds = 25% WEATHER
        else if (cycleSeconds < 600) {
            newDelayType = TrainSimulationState.DelayType.WEATHER;
        }

        // 600 - 700 seconds = 12.5% SIGNAL
        else if (cycleSeconds < 700) {
            newDelayType = TrainSimulationState.DelayType.SIGNAL;
        }

        // 700 - 800 seconds = 12.5% SPEED
        else {
            newDelayType = TrainSimulationState.DelayType.SPEED;
        }

        if (newDelayType != state.getCurrentDelayType()) {

            state.setCurrentDelayType(newDelayType);

            applyDelaySpeed(state);

            printDelayEvent(state);
        }
        else {
            applyDelaySpeed(state);
        }
    }

    // ========================================================
    // APPLY DELAY SPEED
    // ========================================================

    private void applyDelaySpeed(
            TrainSimulationState state
    ) {

        double normalSpeed = getScheduledSpeedKmh(state);

        switch (state.getCurrentDelayType()) {

            case NONE:
                state.setSpeedKmh(normalSpeed);
                break;

            case SPEED:
                state.setSpeedKmh(normalSpeed * SPEED_DELAY_FACTOR);
                break;

            case SIGNAL:
                state.setSpeedKmh(normalSpeed * SIGNAL_DELAY_FACTOR);
                break;

            case WEATHER:
                state.setSpeedKmh(normalSpeed * WEATHER_DELAY_FACTOR);
                break;
        }
    }

    private double getScheduledSpeedKmh(
            TrainSimulationState state
    ) {
        List<Double> speeds =
                scheduledSpeedsKmh.get(state.getTrainNo());

        if (speeds == null || speeds.isEmpty()) {
            return 80.0;
        }

        int segmentIndex = state.getCurrentSegment();

        if (segmentIndex < 0 || segmentIndex >= speeds.size()) {
            return speeds.get(speeds.size() - 1);
        }

        return speeds.get(segmentIndex);
    }

    // ========================================================
    // DELAY EVENT LOGGING
    // ========================================================

    private void printDelayEvent(
            TrainSimulationState state
    ) {

        System.out.println();

        System.out.println(
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
        );

        switch (state.getCurrentDelayType()) {

            case NONE:

                System.out.println(
                        "TRAIN "
                                + state.getTrainNo()
                                + " : DELAY ENDED"
                );

                System.out.println(
                        "Train has returned to normal operation."
                );

                System.out.println(
                        "Speed : "
                                + 80
                                + " km/h"
                );

                break;

            case SPEED:

                System.out.println(
                        "TRAIN "
                                + state.getTrainNo()
                                + " : SPEED DELAY STARTED"
                );

                System.out.println(
                        "Cause : Temporary speed restriction"
                );

                System.out.println(
                        "Speed : "
                                + 45
                                + " km/h"
                );

                break;

            case SIGNAL:

                System.out.println(
                        "TRAIN "
                                + state.getTrainNo()
                                + " : SIGNAL DELAY STARTED"
                );

                System.out.println(
                        "Cause : Red signal / operational hold"
                );

                System.out.println(
                        "Speed : "
                                + 0
                                + " km/h"
                );

                break;

            case WEATHER:

                System.out.println(
                        "TRAIN "
                                + state.getTrainNo()
                                + " : WEATHER DELAY STARTED"
                );

                System.out.println(
                        "Cause : Adverse weather conditions"
                );

                System.out.println(
                        "Speed : "
                                + 50
                                + " km/h"
                );

                break;
        }

        System.out.println(
                "Simulation time : "
                        + state.getSimulationTime()
        );

        System.out.println(
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
        );

        System.out.println();
    }

    // ========================================================
    // GEOMETRY INTERPOLATION
    // ========================================================

    private SimulatedPoint getPointAlongGeometry(
            List<SimulatedPoint> points,
            double travelledKm
    ) {

        if (points == null || points.isEmpty()) {

            throw new RuntimeException(
                    "Segment contains no geometry points"
            );
        }

        // ----------------------------------------------------
        // Only one point
        // ----------------------------------------------------

        if (points.size() == 1) {
            return points.get(0);
        }

        double remainingDistance =
                travelledKm;

        // ----------------------------------------------------
        // Walk through every LineString piece
        // ----------------------------------------------------

        for (
                int i = 0;
                i < points.size() - 1;
                i++
        ) {

            SimulatedPoint start =
                    points.get(i);

            SimulatedPoint end =
                    points.get(i + 1);

            double segmentDistance =
                    calculateDistance(
                            start.latitude(),
                            start.longitude(),
                            end.latitude(),
                            end.longitude()
                    );

            // ------------------------------------------------
            // Train is inside this geometry piece
            // ------------------------------------------------

            if (
                    remainingDistance
                            <= segmentDistance
            ) {

                if (segmentDistance == 0) {
                    return end;
                }

                double progress =
                        remainingDistance
                                / segmentDistance;

                progress =
                        Math.max(
                                0.0,
                                Math.min(1.0, progress)
                        );

                double latitude =
                        start.latitude()
                                +
                                (
                                        end.latitude()
                                                - start.latitude()
                                )
                                        * progress;

                double longitude =
                        start.longitude()
                                +
                                (
                                        end.longitude()
                                                - start.longitude()
                                )
                                        * progress;

                return new SimulatedPoint(
                        latitude,
                        longitude
                );
            }

            remainingDistance -=
                    segmentDistance;
        }

        // ----------------------------------------------------
        // Distance exceeds geometry
        // ----------------------------------------------------

        return points.get(
                points.size() - 1
        );
    }

    // ========================================================
    // HAVERSINE DISTANCE
    // ========================================================

    private double calculateDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        final double EARTH_RADIUS_KM =
                6371.0;

        double lat1 =
                Math.toRadians(latitude1);

        double lat2 =
                Math.toRadians(latitude2);

        double deltaLat =
                Math.toRadians(
                        latitude2 - latitude1
                );

        double deltaLon =
                Math.toRadians(
                        longitude2 - longitude1
                );

        double a =
                Math.sin(deltaLat / 2)
                        * Math.sin(deltaLat / 2)
                        +
                        Math.cos(lat1)
                                * Math.cos(lat2)
                                * Math.sin(deltaLon / 2)
                                * Math.sin(deltaLon / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM * c;
    }

    // ========================================================
    // INITIALIZE ALL TRAINS
    // ========================================================

    private void initializeAllTrains() {

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "MULTI-TRAIN GPS SIMULATOR"
        );
        System.out.println(
                "Loading up to "
                        + MAX_SIMULATED_TRAINS
                        + " trains..."
        );
        System.out.println(
                "========================================"
        );

        List<Train> trains =
                trainRepository.findAllSimulatableTrains();

        int initializedCount = 0;

        for (Train train : trains) {

            if (
                    initializedCount
                            >= MAX_SIMULATED_TRAINS
            ) {
                break;
            }

            String trainNo =
                    train.getTrainNo();

            try {

                TrainSimulationState state =
                        initializeTrain(train);

                if (state != null) {

                    simulations.put(
                            trainNo,
                            state
                    );

                    initializedCount++;

                    System.out.println(
                            "[INIT] Train "
                                    + trainNo
                                    + " | Run ID="
                                    + state.getRunId()
                    );
                }

            } catch (Exception e) {

                /*
                 * One bad/missing route should not stop
                 * all other trains from running.
                 */

                System.err.println(
                        "[SKIP] Train "
                                + trainNo
                                + " could not be initialized: "
                                + e.getMessage()
                );
            }
        }

        initialized = true;

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "MULTI-TRAIN INITIALIZATION COMPLETE"
        );
        System.out.println(
                "Trains running: "
                        + simulations.size()
        );
        System.out.println(
                "========================================"
        );
        System.out.println();
    }

    private List<Double> calculateScheduledSpeeds(
            List<SimulatedSegment> segments,
            List<RouteStation> routeStations
    ) {
        List<Double> speeds = new java.util.ArrayList<>();

        int count = Math.min(
                segments.size(),
                Math.max(0, routeStations.size() - 1)
        );

        for (int i = 0; i < count; i++) {

            RouteStation from = routeStations.get(i);
            RouteStation to = routeStations.get(i + 1);

            LocalTime fromTime =
                    from.getDepartureTime() != null
                            ? from.getDepartureTime()
                            : from.getArrivalTime();

            LocalTime toTime =
                    to.getArrivalTime() != null
                            ? to.getArrivalTime()
                            : to.getDepartureTime();

            if (fromTime == null || toTime == null) {
                speeds.add(80.0);
                continue;
            }

            int fromDay =
                    from.getDay() == null ? 1 : from.getDay();

            int toDay =
                    to.getDay() == null ? fromDay : to.getDay();

            LocalDate baseDate =
                    LocalDate.of(2000, 1, 1);

            LocalDateTime fromDateTime =
                    LocalDateTime.of(
                            baseDate.plusDays(fromDay - 1L),
                            fromTime
                    );

            LocalDateTime toDateTime =
                    LocalDateTime.of(
                            baseDate.plusDays(toDay - 1L),
                            toTime
                    );

            if (!toDateTime.isAfter(fromDateTime)) {
                toDateTime = toDateTime.plusDays(1);
            }

            double travelHours =
                    java.time.Duration.between(
                            fromDateTime,
                            toDateTime
                    ).toSeconds() / 3600.0;

            double distanceKm =
                    segments.get(i).distanceKm();

            double speed =
                    travelHours > 0
                            ? distanceKm / travelHours
                            : 80.0;

            if (!Double.isFinite(speed) || speed <= 0) {
                speed = 80.0;
            }

            speeds.add(speed);
        }

        while (speeds.size() < segments.size()) {
            speeds.add(80.0);
        }

        return speeds;
    }

    // ========================================================
    // INITIALIZE ONE TRAIN
    // ========================================================

    private TrainSimulationState initializeTrain(
            Train train
    ) {

        String trainNo =
                train.getTrainNo();

        // ----------------------------------------------------
        // Load real route geometry
        // ----------------------------------------------------

        List<SimulatedSegment> segments =
                gpsRouteService.loadRoute(
                        trainNo
                );

        if (
                segments == null
                        ||
                        segments.isEmpty()
        ) {

            throw new RuntimeException(
                    "No route segments found"
            );
        }

        // ----------------------------------------------------
        // Load ordered route stations
        // ----------------------------------------------------

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        if (routeStations.isEmpty()) {

            throw new RuntimeException(
                    "No route stations found"
            );
        }

        /*
         * Build the normal speed for every segment from the timetable.
         * This prevents the simulator from arriving early simply because
         * a fixed 80 km/h speed was faster than the real schedule.
         */
        scheduledSpeedsKmh.put(
                trainNo,
                calculateScheduledSpeeds(
                        segments,
                        routeStations
                )
        );

        // ----------------------------------------------------
        // First station departure
        // ----------------------------------------------------

        RouteStation firstStation =
                routeStations.get(0);

        LocalTime departureTime =
                firstStation.getDepartureTime();

        if (departureTime == null) {

            throw new RuntimeException(
                    "No departure time found"
            );
        }

        // ----------------------------------------------------
        // Schedule day
        // ----------------------------------------------------

        int scheduleDay =
                firstStation.getDay() == null
                        ? 1
                        : firstStation.getDay();

        // ----------------------------------------------------
        // Simulation date
        // ----------------------------------------------------

        LocalDate simulationDate =
                LocalDate.now()
                        .plusDays(
                                scheduleDay - 1L
                        );

        ZoneId zone =
                ZoneId.systemDefault();

        // ----------------------------------------------------
        // Simulation clock
        // ----------------------------------------------------

        Instant simulationTime =
                ZonedDateTime.of(
                        simulationDate,
                        departureTime,
                        zone
                ).toInstant();

        // ----------------------------------------------------
        // Start TrainRun
        // ----------------------------------------------------

        TrainRun run =
                trainRunService.startRun(
                        trainNo,
                        simulationTime
                );

        // ----------------------------------------------------
        // Create simulation state
        // ----------------------------------------------------

        return new TrainSimulationState(
                trainNo,
                segments,
                simulationTime,
                run.getId()
        );
    }
}