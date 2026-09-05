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
    private static final double SIMULATION_SPEED_MULTIPLIER = 30.0;
    private static final double NORMAL_SPEED_KMH = 80.0;

    private static final double SPEED_DELAY_KMH = 45.0;

    private static final double WEATHER_DELAY_KMH = 50.0;

    private static final double SIGNAL_DELAY_KMH = 0.0;

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
        // Calculate distance travelled
        //
        // distance = speed × time / 3600
        // ----------------------------------------------------

        double distanceThisTick =
                state.getSpeedKmh()
                        * elapsedSeconds
                        / 3600.0;

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
                        state.getCurrentDelayType()
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

        switch (state.getCurrentDelayType()) {

            case NONE:

                state.setSpeedKmh(
                        NORMAL_SPEED_KMH
                );

                break;

            case SPEED:

                state.setSpeedKmh(
                        SPEED_DELAY_KMH
                );

                break;

            case SIGNAL:

                state.setSpeedKmh(
                        SIGNAL_DELAY_KMH
                );

                break;

            case WEATHER:

                state.setSpeedKmh(
                        WEATHER_DELAY_KMH
                );

                break;
        }
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
                                + NORMAL_SPEED_KMH
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
                                + SPEED_DELAY_KMH
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
                                + SIGNAL_DELAY_KMH
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
                                + WEATHER_DELAY_KMH
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