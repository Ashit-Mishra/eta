package com.railway.eta.eta;

import com.railway.eta.ml.MlPredictionService;
import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.simulator.GpsRouteService;
import com.railway.eta.simulator.SimulatedSegment;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RemainingStationEtaService {

    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final TrainStateService trainStateService;
    private final MlPredictionService mlPredictionService;
    private final GpsRouteService gpsRouteService;

    public RemainingStationEtaService(
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository,
            TrainStateService trainStateService,
            MlPredictionService mlPredictionService,
            GpsRouteService gpsRouteService
    ) {
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
        this.trainStateService = trainStateService;
        this.mlPredictionService = mlPredictionService;
        this.gpsRouteService = gpsRouteService;
    }

    @Transactional(readOnly = true)
    public List<RemainingStationEtaResponse> calculate(
            String trainNo
    ) {

        /*
         * ========================================================
         * 1. Get current live train state
         * ========================================================
         */
        TrainState state =
                trainStateService.get(trainNo);

        if (state == null) {

            throw new RuntimeException(
                    "No live state found for train "
                            + trainNo
            );
        }

        /*
         * If nextStation is null, the train has reached
         * its destination.
         */
        if (state.getNextStation() == null) {
            return List.of();
        }

        /*
         * ========================================================
         * 2. Find train
         * ========================================================
         */
        Train train =
                trainRepository
                        .findByTrainNo(trainNo)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: "
                                                + trainNo
                                )
                        );

        /*
         * ========================================================
         * 3. Load ordered route stations
         * ========================================================
         */
        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        /*
         * ========================================================
         * 4. Load route segments
         * ========================================================
         *
         * GpsRouteService gives us the ordered segments:
         *
         * station[0] -> station[1]
         * station[1] -> station[2]
         * station[2] -> station[3]
         * ...
         */
        List<SimulatedSegment> segments =
                gpsRouteService.loadRoute(trainNo);

        /*
         * ========================================================
         * 5. Find current next-station index
         * ========================================================
         */
        int nextStationIndex = -1;

        for (int i = 0; i < routeStations.size(); i++) {

            String stationCode =
                    routeStations
                            .get(i)
                            .getStation()
                            .getCode();

            if (stationCode.equals(
                    state.getNextStation()
            )) {

                nextStationIndex = i;
                break;
            }
        }

        if (nextStationIndex == -1) {

            throw new RuntimeException(
                    "Next station "
                            + state.getNextStation()
                            + " not found in route"
            );
        }

        /*
         * ========================================================
         * 6. Result list
         * ========================================================
         */
        List<RemainingStationEtaResponse> result =
                new ArrayList<>();

        /*
         * ========================================================
         * 7. Current train speed
         * ========================================================
         */
        double speedKmh =
                state.getSpeedKmh();

        /*
         * ========================================================
         * 8. Distance from current train position
         *    to immediate next station
         * ========================================================
         *
         * This comes directly from TrainState.
         */
        double distanceToTarget =
                state.getDistanceToNextStationKm();

        /*
         * ========================================================
         * 9. Calculate ETA for every remaining station
         * ========================================================
         */
        for (
                int i = nextStationIndex;
                i < routeStations.size();
                i++
        ) {

            RouteStation targetRouteStation =
                    routeStations.get(i);

            String stationCode =
                    targetRouteStation
                            .getStation()
                            .getCode();

            /*
             * ----------------------------------------------------
             * Calculate distance from target station
             * to final destination.
             * ----------------------------------------------------
             *
             * Example:
             *
             * Current → B
             * B → C
             * C → D
             * D → E
             *
             * If target = C:
             *
             * distanceToDestination =
             *     C → D + D → E
             */
            double distanceAfterTarget = 0.0;

            for (
                    int j = i;
                    j < segments.size();
                    j++
            ) {

                distanceAfterTarget +=
                        segments
                                .get(j)
                                .distanceKm();
            }

            /*
             * ----------------------------------------------------
             * Base ETA
             * ----------------------------------------------------
             *
             * This is pure movement ETA:
             *
             * distance / speed * 60
             */
            double baseEtaMinutes = 0.0;

            if (speedKmh > 0) {

                baseEtaMinutes =
                        (
                                distanceToTarget
                                        / speedKmh
                        ) * 60;
            }

            /*
             * ----------------------------------------------------
             * ML prediction
             * ----------------------------------------------------
             *
             * IMPORTANT:
             *
             * distanceToTarget
             * and
             * distanceAfterTarget
             *
             * are now specific to this station.
             */
            double predictedDelay =
                    mlPredictionService.predictDelay(
                            trainNo,
                            state,
                            stationCode,
                            distanceToTarget,
                            distanceAfterTarget
                    );

            /*
             * ----------------------------------------------------
             * Final predicted ETA
             * ----------------------------------------------------
             */
            double predictedEta =
                    baseEtaMinutes
                            + predictedDelay;

            /*
             * ----------------------------------------------------
             * Add response
             * ----------------------------------------------------
             */
            result.add(
                    new RemainingStationEtaResponse(
                            stationCode,
                            baseEtaMinutes,
                            predictedDelay,
                            predictedEta
                    )
            );

            /*
             * ----------------------------------------------------
             * Move to next station
             * ----------------------------------------------------
             *
             * Segment i represents:
             *
             * station[i] -> station[i + 1]
             *
             * Therefore after processing station i,
             * add this segment to distanceToTarget.
             */
            if (i < segments.size()) {

                distanceToTarget +=
                        segments
                                .get(i)
                                .distanceKm();
            }
        }

        /*
         * ========================================================
         * 10. Return all remaining stations
         * ========================================================
         */
        return result;
    }
}