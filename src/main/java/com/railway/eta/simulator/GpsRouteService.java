package com.railway.eta.simulator;

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
        // Find train
        // ----------------------------------------------------

        Train train = trainRepository
                .findByTrainNo(trainNo)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Train not found: " + trainNo
                        )
                );

        // ----------------------------------------------------
        // Get route ID
        // ----------------------------------------------------

        Long routeId = train.getRoute().getId();

        // ----------------------------------------------------
        // Load route stations in sequence
        // ----------------------------------------------------

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                routeId
                        );

        if (routeStations.size() < 2) {
            throw new RuntimeException(
                    "Train route must contain at least two stations"
            );
        }

        // ----------------------------------------------------
        // Convert JPA entities to plain objects
        //
        // This happens INSIDE the transaction so that
        // lazy-loaded Station entities can be accessed safely.
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
        // Build segments
        //
        // 69 stations = 68 segments
        //
        // NDLS → DSB
        // DSB  → SZM
        // SZM  → DAZ
        // ...
        // ----------------------------------------------------

        List<SimulatedSegment> segments =
                new ArrayList<>();

        for (int i = 0; i < stations.size() - 1; i++) {

            SimulatedStation from =
                    stations.get(i);

            SimulatedStation to =
                    stations.get(i + 1);

            double distanceKm =
                    calculateDistance(
                            from.latitude(),
                            from.longitude(),
                            to.latitude(),
                            to.longitude()
                    );

            segments.add(
                    new SimulatedSegment(
                            from,
                            to,
                            distanceKm
                    )
            );
        }

        return segments;
    }


    // --------------------------------------------------------
    // Haversine distance calculation
    // --------------------------------------------------------

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