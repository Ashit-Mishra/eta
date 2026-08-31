package com.railway.eta.eta;

import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.section.Section;
import com.railway.eta.section.SectionRepository;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrainStateService {

    private final Map<String, TrainState> liveTrains =
            new ConcurrentHashMap<>();

    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final SectionRepository sectionRepository;

    public TrainStateService(
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository,
            SectionRepository sectionRepository
    ) {
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
        this.sectionRepository = sectionRepository;
    }

    @Transactional
    public void update(TrainState state) {

        Train train =
                trainRepository
                        .findByTrainNo(state.getTrainNo())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: "
                                                + state.getTrainNo()
                                )
                        );

        Long routeId =
                train.getRoute().getId();

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                routeId
                        );

        if (routeStations.isEmpty()) {

            liveTrains.put(
                    state.getTrainNo(),
                    state
            );

            return;
        }

        // ----------------------------------------------------
        // Find nearest station
        // ----------------------------------------------------

        int nearestStationIndex =
                findNearestStation(
                        state.getLatitude(),
                        state.getLongitude(),
                        routeStations
                );

        RouteStation nearest =
                routeStations.get(nearestStationIndex);

        state.setCurrentStation(
                nearest.getStation().getCode()
        );

        // ----------------------------------------------------
        // Determine next station
        // ----------------------------------------------------

        if (nearestStationIndex < routeStations.size() - 1) {

            RouteStation next =
                    routeStations.get(
                            nearestStationIndex + 1
                    );

            state.setNextStation(
                    next.getStation().getCode()
            );

            // Distance from current GPS position
            // to next station
            double distanceToNext =
                    calculateDistance(
                            state.getLatitude(),
                            state.getLongitude(),
                            next.getStation().getLatitude(),
                            next.getStation().getLongitude()
                    );

            state.setDistanceToNextStationKm(
                    distanceToNext
            );

            // ------------------------------------------------
            // Calculate total remaining route distance
            // ------------------------------------------------

            double distanceToDestination =
                    calculateRemainingRouteDistance(
                            state,
                            routeStations,
                            nearestStationIndex
                    );

            state.setDistanceToDestinationKm(
                    distanceToDestination
            );

        } else {

            // Final station reached

            state.setNextStation(null);

            state.setDistanceToNextStationKm(0.0);

            state.setDistanceToDestinationKm(0.0);

            state.setStatus("ARRIVED");
        }

        // ----------------------------------------------------
        // Save live state
        // ----------------------------------------------------

        liveTrains.put(
                state.getTrainNo(),
                state
        );
    }


    // ========================================================
    // Calculate remaining route distance
    // ========================================================

    private double calculateRemainingRouteDistance(
            TrainState state,
            List<RouteStation> routeStations,
            int currentIndex
    ) {

        double totalDistance = 0.0;

        // ----------------------------------------------------
        // Distance from current GPS position
        // to the next station
        // ----------------------------------------------------

        RouteStation next =
                routeStations.get(
                        currentIndex + 1
                );

        totalDistance +=
                calculateDistance(
                        state.getLatitude(),
                        state.getLongitude(),
                        next.getStation().getLatitude(),
                        next.getStation().getLongitude()
                );


        // ----------------------------------------------------
        // Add all remaining station-to-station sections
        // ----------------------------------------------------

        for (
                int i = currentIndex + 1;
                i < routeStations.size() - 1;
                i++
        ) {

            RouteStation from =
                    routeStations.get(i);

            RouteStation to =
                    routeStations.get(i + 1);


            Section section =
                    sectionRepository
                            .findByFromStationIdAndToStationId(
                                    from.getStation().getId(),
                                    to.getStation().getId()
                            )
                            .orElse(null);


            if (section != null
                    && section.getDistanceKm() != null) {

                totalDistance +=
                        section.getDistanceKm();

            } else {

                // Fallback if section data is missing
                totalDistance +=
                        calculateDistance(
                                from.getStation().getLatitude(),
                                from.getStation().getLongitude(),
                                to.getStation().getLatitude(),
                                to.getStation().getLongitude()
                        );
            }
        }

        return totalDistance;
    }


    // ========================================================
    // Find nearest station
    // ========================================================

    private int findNearestStation(
            double latitude,
            double longitude,
            List<RouteStation> stations
    ) {

        int nearestIndex = 0;

        double minimumDistance =
                Double.MAX_VALUE;

        for (int i = 0; i < stations.size(); i++) {

            var station =
                    stations
                            .get(i)
                            .getStation();

            if (station.getLatitude() == null
                    || station.getLongitude() == null) {

                continue;
            }

            double distance =
                    calculateDistance(
                            latitude,
                            longitude,
                            station.getLatitude(),
                            station.getLongitude()
                    );

            if (distance < minimumDistance) {

                minimumDistance = distance;

                nearestIndex = i;
            }
        }

        return nearestIndex;
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


    // ========================================================
    // Get one train
    // ========================================================

    public TrainState get(String trainNo) {

        return liveTrains.get(trainNo);
    }


    // ========================================================
    // Get all trains
    // ========================================================

    public Map<String, TrainState> getAll() {

        return liveTrains;
    }
}