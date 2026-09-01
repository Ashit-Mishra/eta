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

@Component
public class GpsSimulator {

    private final GpsEventProducer producer;
    private final GpsRouteService gpsRouteService;
    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final TrainRunService trainRunService;

    // ========================================================
    // REAL ROUTE
    // ========================================================

    private List<SimulatedSegment> segments;

    private int currentSegment = 0;

    private double distanceTravelledKm = 0.0;


    // ========================================================
    // SPEED
    // ========================================================

    private static final double NORMAL_SPEED_KMH = 80.0;

    private static final double SPEED_DELAY_KMH = 45.0;

    private static final double WEATHER_DELAY_KMH = 50.0;

    private static final double SIGNAL_DELAY_KMH = 0.0;

    private double speedKmh = NORMAL_SPEED_KMH;


    // ========================================================
    // SIMULATION CLOCK
    // ========================================================

    private Instant simulationTime;

    private long lastRealTime;


    // ========================================================
    // DELAY SIMULATION
    // ========================================================

    /*
     * The simulation intentionally runs through three
     * different delay scenarios so we can verify the
     * backend before building historical data.
     *
     * Timeline:
     *
     * 0  - 30 sec   NORMAL
     * 30 - 75 sec   SPEED
     * 75 - 105 sec  NORMAL
     * 105 - 135 sec SIGNAL
     * 135 - 165 sec NORMAL
     * 165 - 225 sec WEATHER
     * 225+ sec      NORMAL
     */

    private enum DelayType {
        NONE,
        SPEED,
        SIGNAL,
        WEATHER
    }

    private DelayType currentDelayType = DelayType.NONE;


    // ========================================================
    // TRAIN
    // ========================================================

    private final String trainNo = "12031";


    // ========================================================
    // SIMULATION START
    // ========================================================

    private Instant simulationStartTime;

    private Long currentRunId;


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

    /**
     * Generate one GPS position every 2 seconds.
     */
    @Scheduled(fixedRate = 2000)
    public void generateGpsEvent() {

        // ----------------------------------------------------
        // Initialize route
        // ----------------------------------------------------

        if (segments == null) {
            initialize();
        }

        if (segments.isEmpty()) {

            System.out.println(
                    "No route segments found for train "
                            + trainNo
            );

            return;
        }


        // ----------------------------------------------------
        // Calculate elapsed real time
        // ----------------------------------------------------

        long currentTime =
                System.currentTimeMillis();

        double elapsedSeconds =
                (currentTime - lastRealTime)
                        / 1000.0;

        lastRealTime = currentTime;


        // ----------------------------------------------------
        // Advance simulation clock
        // ----------------------------------------------------

        simulationTime =
                simulationTime.plusMillis(
                        (long) (elapsedSeconds * 1000)
                );


        // ----------------------------------------------------
        // Update delay scenario
        // ----------------------------------------------------

        updateDelayState();


        // ----------------------------------------------------
        // Calculate distance travelled
        //
        // distance = speed × time / 3600
        // ----------------------------------------------------

        double distanceThisTick =
                speedKmh
                        * elapsedSeconds
                        / 3600.0;

        distanceTravelledKm +=
                distanceThisTick;


        // ----------------------------------------------------
        // Check whether station/segment reached
        // ----------------------------------------------------

        while (
                currentSegment < segments.size()
                        &&
                        distanceTravelledKm >=
                                segments
                                        .get(currentSegment)
                                        .distanceKm()
        ) {

            double segmentDistance =
                    segments
                            .get(currentSegment)
                            .distanceKm();

            distanceTravelledKm -=
                    segmentDistance;

            currentSegment++;


            // ------------------------------------------------
            // Destination reached
            // ------------------------------------------------

            if (currentSegment >= segments.size()) {

                System.out.println(
                        "========================================"
                );

                System.out.println(
                        "Train "
                                + trainNo
                                + " reached its destination."
                );

                System.out.println(
                        "Simulation time: "
                                + simulationTime
                );

                System.out.println(
                        "========================================"
                );


                // --------------------------------------------
                // Complete TrainRun
                // --------------------------------------------

                if (currentRunId != null) {

                    /*
                     * Temporary value.
                     *
                     * We will replace this with the actual
                     * schedule-vs-actual destination delay
                     * in the next step.
                     */
                    double finalDelayMinutes = 0.0;

                    trainRunService.completeRun(
                            currentRunId,
                            simulationTime,
                            finalDelayMinutes
                    );

                    currentRunId = null;
                }

                return;
            }
        }


        // ----------------------------------------------------
        // Get current segment
        // ----------------------------------------------------

        SimulatedSegment segment =
                segments.get(currentSegment);

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
                    distanceTravelledKm
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
                        distanceTravelledKm
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
                        trainNo,
                        currentRunId,
                        latitude,
                        longitude,
                        speedKmh,
                        simulationTime,
                        from.code(),
                        to.code()
                );


        // ----------------------------------------------------
        // Send GPS event to Kafka
        // ----------------------------------------------------

        producer.send(event);


        // ----------------------------------------------------
        // Console logging
        // ----------------------------------------------------

        System.out.println(
                "========================================"
        );

        System.out.println(
                "GPS Simulator"
        );

        System.out.println(
                "Train       : " + trainNo
        );

        System.out.println(
                "From        : " + from.code()
        );

        System.out.println(
                "To          : " + to.code()
        );

        System.out.printf(
                "Segment     : %.3f km%n",
                segment.distanceKm()
        );

        System.out.printf(
                "Travelled   : %.3f km%n",
                distanceTravelledKm
        );

        System.out.printf(
                "Progress    : %.2f%%%n",
                progress * 100
        );

        System.out.printf(
                "Speed       : %.1f km/h%n",
                speedKmh
        );

        System.out.println(
                "Delay Type  : " + currentDelayType
        );

        System.out.printf(
                "GPS         : %.6f, %.6f%n",
                latitude,
                longitude
        );

        System.out.println(
                "Simulation  : " + simulationTime
        );

        System.out.println(
                "========================================"
        );
    }


    // ========================================================
    // DELAY CONTROLLER
    // ========================================================

    private void updateDelayState() {

        if (simulationStartTime == null) {
            return;
        }


        long elapsedSimulationSeconds =
                java.time.Duration
                        .between(
                                simulationStartTime,
                                simulationTime
                        )
                        .getSeconds();


        DelayType newDelayType;


        // ----------------------------------------------------
        // 0 - 30 seconds
        // NORMAL
        // ----------------------------------------------------

        if (elapsedSimulationSeconds < 30) {

            newDelayType =
                    DelayType.NONE;
        }


        // ----------------------------------------------------
        // 30 - 75 seconds
        // SPEED DELAY
        // ----------------------------------------------------

        else if (elapsedSimulationSeconds < 75) {

            newDelayType =
                    DelayType.SPEED;
        }


        // ----------------------------------------------------
        // 75 - 105 seconds
        // NORMAL
        // ----------------------------------------------------

        else if (elapsedSimulationSeconds < 105) {

            newDelayType =
                    DelayType.NONE;
        }


        // ----------------------------------------------------
        // 105 - 135 seconds
        // SIGNAL DELAY
        // ----------------------------------------------------

        else if (elapsedSimulationSeconds < 135) {

            newDelayType =
                    DelayType.SIGNAL;
        }


        // ----------------------------------------------------
        // 135 - 165 seconds
        // NORMAL
        // ----------------------------------------------------

        else if (elapsedSimulationSeconds < 165) {

            newDelayType =
                    DelayType.NONE;
        }


        // ----------------------------------------------------
        // 165 - 225 seconds
        // WEATHER DELAY
        // ----------------------------------------------------

        else if (elapsedSimulationSeconds < 225) {

            newDelayType =
                    DelayType.WEATHER;
        }


        // ----------------------------------------------------
        // 225+ seconds
        // NORMAL
        // ----------------------------------------------------

        else {

            newDelayType =
                    DelayType.NONE;
        }


        // ----------------------------------------------------
        // Only print when state changes
        // ----------------------------------------------------

        if (newDelayType != currentDelayType) {

            currentDelayType =
                    newDelayType;

            applyDelaySpeed();

            printDelayEvent();
        }

        else {

            // Make sure speed stays correct.
            applyDelaySpeed();
        }
    }


    // ========================================================
    // APPLY DELAY SPEED
    // ========================================================

    private void applyDelaySpeed() {

        switch (currentDelayType) {

            case NONE:

                speedKmh =
                        NORMAL_SPEED_KMH;

                break;


            case SPEED:

                speedKmh =
                        SPEED_DELAY_KMH;

                break;


            case SIGNAL:

                speedKmh =
                        SIGNAL_DELAY_KMH;

                break;


            case WEATHER:

                speedKmh =
                        WEATHER_DELAY_KMH;

                break;
        }
    }


    // ========================================================
    // DELAY EVENT LOGGING
    // ========================================================

    private void printDelayEvent() {

        System.out.println();

        System.out.println(
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
        );


        switch (currentDelayType) {

            case NONE:

                System.out.println(
                        "DELAY ENDED"
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
                        "SPEED DELAY STARTED"
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
                        "SIGNAL DELAY STARTED"
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
                        "WEATHER DELAY STARTED"
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
                        + simulationTime
        );

        System.out.println(
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
        );

        System.out.println();
    }


    // ========================================================
    // GEOMETRY INTERPOLATION
    // ========================================================

    /**
     * Find the GPS position corresponding to a distance
     * travelled along the LineString.
     */
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

            if (remainingDistance <= segmentDistance) {

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
    // INITIALIZATION
    // ========================================================

    /**
     * Load the real train route from PostgreSQL.
     */
    private void initialize() {

        segments =
                gpsRouteService.loadRoute(trainNo);


        lastRealTime =
                System.currentTimeMillis();


        currentSegment = 0;

        distanceTravelledKm = 0.0;

        speedKmh =
                NORMAL_SPEED_KMH;

        currentDelayType =
                DelayType.NONE;


        // ----------------------------------------------------
        // Load train
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
        // Load ordered route stations
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
        // First station departure
        // ----------------------------------------------------

        RouteStation firstStation =
                routeStations.get(0);


        LocalTime departureTime =
                firstStation.getDepartureTime();


        if (departureTime == null) {

            throw new RuntimeException(
                    "No departure time found for train "
                            + trainNo
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

        simulationTime =
                ZonedDateTime.of(
                        simulationDate,
                        departureTime,
                        zone
                ).toInstant();


        // ----------------------------------------------------
        // Simulation start
        // ----------------------------------------------------

        simulationStartTime =
                simulationTime;


        // ----------------------------------------------------
        // START TRAIN RUN
        // ----------------------------------------------------

        TrainRun run =
                trainRunService.startRun(
                        trainNo,
                        simulationStartTime
                );

        currentRunId =
                run.getId();


        // ----------------------------------------------------
        // Logging
        // ----------------------------------------------------

        System.out.println(
                "========================================"
        );

        System.out.println(
                "GPS Simulator initialized"
        );

        System.out.println(
                "Train    : " + trainNo
        );

        System.out.println(
                "Segments : " + segments.size()
        );

        System.out.println(
                "Schedule : " + simulationTime
        );

        System.out.println(
                "Run ID   : " + currentRunId
        );

        System.out.println(
                "Normal   : "
                        + NORMAL_SPEED_KMH
                        + " km/h"
        );

        System.out.println(
                "Speed delay : "
                        + SPEED_DELAY_KMH
                        + " km/h"
        );

        System.out.println(
                "Signal delay : "
                        + SIGNAL_DELAY_KMH
                        + " km/h"
        );

        System.out.println(
                "Weather delay : "
                        + WEATHER_DELAY_KMH
                        + " km/h"
        );

        System.out.println(
                "========================================"
        );
    }
}