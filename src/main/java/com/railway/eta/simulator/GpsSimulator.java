package com.railway.eta.simulator;

import com.railway.eta.ingestion.dto.GpsEvent;
import com.railway.eta.ingestion.kafka.GpsEventProducer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class GpsSimulator {

    private final GpsEventProducer producer;
    private final GpsRouteService gpsRouteService;

    // Real route segments loaded from PostgreSQL
    private List<SimulatedSegment> segments;

    // Which segment the train is currently travelling on
    private int currentSegment = 0;

    // Distance travelled on the current segment
    private double distanceTravelledKm = 0.0;

    // Current simulated speed
    private double speedKmh = 80.0;

    // Used to calculate how much real time passed
    private long lastUpdateTime;

    // Train we are currently simulating
    private final String trainNo = "12031";


    public GpsSimulator(
            GpsEventProducer producer,
            GpsRouteService gpsRouteService
    ) {
        this.producer = producer;
        this.gpsRouteService = gpsRouteService;
    }


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
        // Calculate elapsed time
        // ----------------------------------------------------

        long currentTime =
                System.currentTimeMillis();

        double elapsedSeconds =
                (currentTime - lastUpdateTime)
                        / 1000.0;

        lastUpdateTime = currentTime;


        // ----------------------------------------------------
        // Convert speed into distance travelled
        //
        // speed = km/hour
        // time  = seconds
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
                        distanceTravelledKm
                                >= segments
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
        // Calculate progress through current segment
        // ----------------------------------------------------

        double progress =
                distanceTravelledKm
                        / segment.distanceKm();

        // Protect against floating-point issues
        progress =
                Math.max(
                        0.0,
                        Math.min(1.0, progress)
                );


        // ----------------------------------------------------
        // Interpolate GPS position
        // ----------------------------------------------------

        double latitude =
                from.latitude()
                        +
                        (
                                to.latitude()
                                        - from.latitude()
                        )
                                * progress;

        double longitude =
                from.longitude()
                        +
                        (
                                to.longitude()
                                        - from.longitude()
                        )
                                * progress;


        // ----------------------------------------------------
        // Create GPS event
        // ----------------------------------------------------

        GpsEvent event =
                new GpsEvent(
                        trainNo,
                        latitude,
                        longitude,
                        speedKmh,
                        Instant.now()
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
                "========================================"
        );
    }


    /**
     * Load the real train route from PostgreSQL.
     */
    private void initialize() {

        segments =
                gpsRouteService.loadRoute(trainNo);

        lastUpdateTime =
                System.currentTimeMillis();

        currentSegment = 0;

        distanceTravelledKm = 0.0;

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
                "========================================"
        );
    }
}