package com.railway.eta.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class GpsRouteService {

    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GpsRouteService(
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository
    ) {
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
    }

    @Transactional(readOnly = true)
    public List<SimulatedSegment> loadRoute(String trainNo) {

        // ----------------------------------------------------
        // 1. Find train
        // ----------------------------------------------------

        Train train = trainRepository
                .findByTrainNo(trainNo)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Train not found: " + trainNo
                        )
                );

        // ----------------------------------------------------
        // 2. Get route
        // ----------------------------------------------------

        var route = train.getRoute();

        Long routeId = route.getId();

        // ----------------------------------------------------
        // 3. Load stations in correct order
        // ----------------------------------------------------

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(routeId);

        if (routeStations.size() < 2) {
            throw new RuntimeException(
                    "Route must contain at least two stations"
            );
        }

        // ----------------------------------------------------
        // 4. Convert database stations into simulation objects
        // ----------------------------------------------------

        List<SimulatedStation> stations =
                routeStations.stream()
                        .map(routeStation -> {

                            var station =
                                    routeStation.getStation();

                            if (station.getLatitude() == null
                                    || station.getLongitude() == null) {

                                throw new RuntimeException(
                                        "Missing coordinates for station "
                                                + station.getCode()
                                );
                            }

                            return new SimulatedStation(
                                    station.getCode(),
                                    station.getName(),
                                    station.getLatitude(),
                                    station.getLongitude(),
                                    routeStation.getSequenceNumber()
                            );
                        })
                        .toList();

        // ----------------------------------------------------
        // 5. Parse real LineString
        // ----------------------------------------------------

        List<SimulatedPoint> geometryPoints =
                parseGeometry(route.getGeometryJson());

        System.out.println(
                "Loaded real LineString with "
                        + geometryPoints.size()
                        + " points"
        );

        // ----------------------------------------------------
        // 6. Current dataset:
        //
        // 69 stations
        // 69 geometry points
        //
        // Therefore each geometry point corresponds to the
        // station at the same sequence position.
        // ----------------------------------------------------

        if (geometryPoints.size() != stations.size()) {

            throw new RuntimeException(
                    "Station/geometry count mismatch. "
                            + "Stations = "
                            + stations.size()
                            + ", Geometry points = "
                            + geometryPoints.size()
            );
        }

        // ----------------------------------------------------
        // 7. Build segments
        // ----------------------------------------------------

        List<SimulatedSegment> segments =
                new ArrayList<>();

        for (int i = 0; i < stations.size() - 1; i++) {

            SimulatedStation from =
                    stations.get(i);

            SimulatedStation to =
                    stations.get(i + 1);

            /*
             * For now each station-to-station segment contains
             * the LineString points between those stations.
             *
             * With the current dataset there is one geometry
             * point per station, so each segment contains:
             *
             *     point[i]
             *     point[i + 1]
             */

            List<SimulatedPoint> segmentGeometry =
                    List.of(
                            geometryPoints.get(i),
                            geometryPoints.get(i + 1)
                    );

            // Calculate actual distance along the geometry
            double distanceKm =
                    calculateGeometryDistance(
                            segmentGeometry
                    );

            segments.add(
                    new SimulatedSegment(
                            from,
                            to,
                            distanceKm,
                            segmentGeometry
                    )
            );

            System.out.printf(
                    "%s → %s | %.3f km | %d geometry points%n",
                    from.code(),
                    to.code(),
                    distanceKm,
                    segmentGeometry.size()
            );
        }

        return segments;
    }

    // ========================================================
    // Parse GeoJSON LineString
    // ========================================================

    private List<SimulatedPoint> parseGeometry(
            String geometryJson
    ) {

        if (geometryJson == null
                || geometryJson.isBlank()) {

            throw new RuntimeException(
                    "Route has no geometry"
            );
        }

        try {

            JsonNode geometry =
                    objectMapper.readTree(geometryJson);

            JsonNode type =
                    geometry.get("type");

            if (type == null
                    || !"LineString".equals(type.asText())) {

                throw new RuntimeException(
                        "Route geometry is not a LineString"
                );
            }

            JsonNode coordinates =
                    geometry.get("coordinates");

            if (coordinates == null
                    || !coordinates.isArray()) {

                throw new RuntimeException(
                        "LineString has no valid coordinates"
                );
            }

            List<SimulatedPoint> points =
                    new ArrayList<>();

            for (JsonNode coordinate : coordinates) {

                if (!coordinate.isArray()
                        || coordinate.size() < 2) {

                    throw new RuntimeException(
                            "Invalid coordinate in LineString"
                    );
                }

                /*
                 * GeoJSON:
                 *
                 * [longitude, latitude]
                 */

                double longitude =
                        coordinate
                                .get(0)
                                .asDouble();

                double latitude =
                        coordinate
                                .get(1)
                                .asDouble();

                points.add(
                        new SimulatedPoint(
                                latitude,
                                longitude
                        )
                );
            }

            return points;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse route geometry",
                    e
            );
        }
    }

    // ========================================================
    // Calculate total distance along geometry
    // ========================================================

    private double calculateGeometryDistance(
            List<SimulatedPoint> points
    ) {

        double totalDistance = 0.0;

        for (int i = 0; i < points.size() - 1; i++) {

            SimulatedPoint a =
                    points.get(i);

            SimulatedPoint b =
                    points.get(i + 1);

            totalDistance +=
                    calculateDistance(
                            a.latitude(),
                            a.longitude(),
                            b.latitude(),
                            b.longitude()
                    );
        }

        return totalDistance;
    }

    // ========================================================
    // Haversine distance
    // ========================================================

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
}