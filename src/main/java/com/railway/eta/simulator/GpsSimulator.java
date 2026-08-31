package com.railway.eta.simulator;

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

    // ========================================================
    // Route
    // ========================================================

    // Real route segments loaded from PostgreSQL
    private List<SimulatedSegment> segments;

    // Which segment the train is currently travelling on
    private int currentSegment = 0;

    // Distance travelled on the current segment
    private double distanceTravelledKm = 0.0;


    // ========================================================
    // Speed
    // ========================================================

    // Normal train speed
    private static final double NORMAL_SPEED_KMH = 80.0;

    // Speed during simulated disruption
    private static final double DELAY_SPEED_KMH = 45.0;

    private double speedKmh = NORMAL_SPEED_KMH;


    // ========================================================
    // Simulation clock
    // ========================================================

    private Instant simulationTime;

    // Real system time used to advance simulation clock
    private long lastRealTime;


    // ========================================================
    // Controlled delay simulation
    // ========================================================

    /*
     * The slowdown will start 30 seconds after the simulation
     * begins.
     *
     * This is deliberately short so that we can easily test
     * the delay system.
     */
    private static final long DELAY_START_AFTER_SECONDS = 30;


    /*
     * The slowdown lasts for 45 seconds.
     */
    private static final long DELAY_DURATION_SECONDS = 45;


    /*
     * Keeps track of when the simulation started.
     */
    private Instant simulationStartTime;


    /*
     * Prevents the slowdown from being triggered repeatedly.
     */
    private boolean delayTriggered = false;

    private boolean delayActive = false;


    // ========================================================
    // Train
    // ========================================================

    private final String trainNo = "12031";


    // ========================================================
    // Constructor
    // ========================================================

    public GpsSimulator(
            GpsEventProducer producer,
            GpsRouteService gpsRouteService,
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository
    ) {
        this.producer = producer;
        this.gpsRouteService = gpsRouteService;
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
    }


    // ========================================================
    // Main simulation loop
    // ========================================================

    /**
     * Generate one GPS position every 2 seconds.
     */
    @Scheduled(fixedRate = 2000)
    public void generateGpsEvent() {

        // ----------------------------------------------------
        // Initialize route on first execution
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
        // Check for simulated delay
        // ----------------------------------------------------

        updateDelayState();


        // ----------------------------------------------------
        // Convert speed into distance travelled
        //
        // distance = speed × time / 3600
        // ----------------------------------------------------

        double distanceThisTick =
                speedKmh
                        * elapsedSeconds
                        / 3600.0;

        distanceTravelledKm += distanceThisTick;


        // ----------------------------------------------------
        // Check whether we reached the next station
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
                        latitude,
                        longitude,
                        speedKmh,
                        simulationTime
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

        System.out.printf(
                "GPS         : %.6f, %.6f%n",
                latitude,
                longitude
        );

        System.out.println(
                "Simulation  : " + simulationTime
        );

        System.out.println(
                "Status      : "
                        + (delayActive
                        ? "DELAY / SLOWDOWN"
                        : "NORMAL")
        );

        System.out.println(
                "========================================"
        );
    }


    // ========================================================
    // Delay simulation
    // ========================================================

    private void updateDelayState() {

        if (simulationStartTime == null) {
            return;
        }


        // ----------------------------------------------------
        // Calculate how long the simulation has been running
        // ----------------------------------------------------

        long simulationElapsedSeconds =
                java.time.Duration
                        .between(
                                simulationStartTime,
                                simulationTime
                        )
                        .getSeconds();


        // ----------------------------------------------------
        // Start delay
        // ----------------------------------------------------

        if (
                !delayTriggered
                        &&
                        simulationElapsedSeconds
                                >= DELAY_START_AFTER_SECONDS
        ) {

            delayTriggered = true;

            delayActive = true;

            speedKmh = DELAY_SPEED_KMH;


            System.out.println();
            System.out.println(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            );

            System.out.println(
                    "SIMULATED DELAY STARTED"
            );

            System.out.println(
                    "Train       : " + trainNo
            );

            System.out.println(
                    "Normal speed: "
                            + NORMAL_SPEED_KMH
                            + " km/h"
            );

            System.out.println(
                    "Delay speed : "
                            + DELAY_SPEED_KMH
                            + " km/h"
            );

            System.out.println(
                    "Duration    : "
                            + DELAY_DURATION_SECONDS
                            + " seconds"
            );

            System.out.println(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            );

            System.out.println();
        }


        // ----------------------------------------------------
        // End delay
        // ----------------------------------------------------

        if (
                delayActive
                        &&
                        simulationElapsedSeconds
                                >=
                                (
                                        DELAY_START_AFTER_SECONDS
                                                + DELAY_DURATION_SECONDS
                                )
        ) {

            delayActive = false;

            speedKmh = NORMAL_SPEED_KMH;


            System.out.println();
            System.out.println(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            );

            System.out.println(
                    "SIMULATED DELAY ENDED"
            );

            System.out.println(
                    "Train       : " + trainNo
            );

            System.out.println(
                    "Speed       : "
                            + NORMAL_SPEED_KMH
                            + " km/h"
            );

            System.out.println(
                    "Train has returned to normal speed."
            );

            System.out.println(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            );

            System.out.println();
        }
    }


    // ========================================================
    // Geometry interpolation
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

        for (int i = 0; i < points.size() - 1; i++) {

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
            // Is our train position inside this piece?
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
        // If distance exceeds geometry, return final point
        // ----------------------------------------------------

        return points.get(
                points.size() - 1
        );
    }


    // ========================================================
    // Haversine distance
    // ========================================================

    /**
     * Haversine distance between two GPS coordinates.
     */
    private double calculateDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        final double EARTH_RADIUS_KM = 6371.0;


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
    // Initialization
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

        speedKmh = NORMAL_SPEED_KMH;

        delayTriggered = false;

        delayActive = false;


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
        // Get first station departure time
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
        // Get schedule day
        // ----------------------------------------------------

        int scheduleDay =
                firstStation.getDay() == null
                        ? 1
                        : firstStation.getDay();


        // ----------------------------------------------------
        // Create simulation date
        // ----------------------------------------------------

        LocalDate simulationDate =
                LocalDate.now()
                        .plusDays(scheduleDay - 1L);


        ZoneId zone =
                ZoneId.systemDefault();


        // ----------------------------------------------------
        // Initialize simulation clock
        // ----------------------------------------------------

        simulationTime =
                ZonedDateTime.of(
                        simulationDate,
                        departureTime,
                        zone
                ).toInstant();


        // ----------------------------------------------------
        // Store simulation start
        // ----------------------------------------------------

        simulationStartTime =
                simulationTime;


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
                "Normal speed : "
                        + NORMAL_SPEED_KMH
                        + " km/h"
        );

        System.out.println(
                "Delay speed  : "
                        + DELAY_SPEED_KMH
                        + " km/h"
        );

        System.out.println(
                "Delay starts : "
                        + DELAY_START_AFTER_SECONDS
                        + " sec"
        );

        System.out.println(
                "Delay lasts  : "
                        + DELAY_DURATION_SECONDS
                        + " sec"
        );

        System.out.println(
                "========================================"
        );
    }
}