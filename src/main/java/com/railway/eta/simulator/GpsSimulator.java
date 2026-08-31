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

    // Used to calculate elapsed time
    private long lastUpdateTime;

    // Train currently being simulated
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


        // Only one point
        if (points.size() == 1) {
            return points.get(0);
        }


        double remainingDistance =
                travelledKm;


        // ----------------------------------------------------
        // Walk through every LineString segment
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
            // Is our train position inside this geometry piece?
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