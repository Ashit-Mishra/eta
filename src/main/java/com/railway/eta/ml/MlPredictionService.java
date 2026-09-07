package com.railway.eta.ml;

import com.railway.eta.eta.DelayCalculationService;
import com.railway.eta.history.HistoricalDelayService;
import com.railway.eta.eta.TrainState;
import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class MlPredictionService {

    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final DelayCalculationService delayCalculationService;
    private final HistoricalDelayService historicalDelayService;
    private final RestClient restClient;
    private final String predictionUrl;

    public MlPredictionService(
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository,
            DelayCalculationService delayCalculationService,
            HistoricalDelayService historicalDelayService,
            @Value("${ml.prediction.url}") String predictionUrl
    ) {
        this.trainRepository = trainRepository;
        this.routeStationRepository = routeStationRepository;
        this.delayCalculationService = delayCalculationService;
        this.historicalDelayService = historicalDelayService;
        this.restClient = RestClient.create();
        this.predictionUrl = predictionUrl;
    }

    /*
     * ============================================================
     * EXISTING METHOD
     * ============================================================
     *
     * Used by:
     *
     * /api/eta/{trainNo}
     * /api/eta/station/{trainNo}
     *
     * It predicts delay for the immediate next station.
     */
    @Transactional(readOnly = true)
    public double predictDelay(
            String trainNo,
            TrainState state
    ) {

        if (state.getNextStation() == null) {
            return 0.0;
        }

        return predictDelay(
                trainNo,
                state,
                state.getNextStation()
        );
    }

    /*
     * ============================================================
     * EXISTING TARGET-STATION METHOD
     * ============================================================
     *
     * Kept so existing functionality continues to work.
     *
     * This method uses the current state's distances.
     */
    @Transactional(readOnly = true)
    public double predictDelay(
            String trainNo,
            TrainState state,
            String targetStation
    ) {

        if (targetStation == null) {
            return 0.0;
        }

        return predictDelay(
                trainNo,
                state,
                targetStation,
                state.getDistanceToNextStationKm(),
                state.getDistanceToDestinationKm()
        );
    }

    /*
     * ============================================================
     * NEW TARGET-SPECIFIC METHOD
     * ============================================================
     *
     * This is used by RemainingStationEtaService.
     *
     * The important difference is:
     *
     * distanceToTargetKm
     * and
     * distanceToDestinationKm
     *
     * are calculated specifically for the target station.
     */
    @Transactional(readOnly = true)
    public double predictDelay(
            String trainNo,
            TrainState state,
            String targetStation,
            double distanceToTargetKm,
            double distanceToDestinationKm
    ) {

        if (targetStation == null) {
            return 0.0;
        }

        /*
         * --------------------------------------------------------
         * Find train
         * --------------------------------------------------------
         */
        Train train =
                trainRepository
                        .findByTrainNo(trainNo)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: " + trainNo
                                )
                        );

        /*
         * --------------------------------------------------------
         * Load ordered route stations
         * --------------------------------------------------------
         */
        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        /*
         * --------------------------------------------------------
         * Find target station in route
         * --------------------------------------------------------
         */
        RouteStation targetRouteStation =
                routeStations.stream()
                        .filter(routeStation ->
                                routeStation
                                        .getStation()
                                        .getCode()
                                        .equals(targetStation)
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Station "
                                                + targetStation
                                                + " not found in route for train "
                                                + trainNo
                                )
                        );

        /*
         * --------------------------------------------------------
         * Scheduled arrival
         * --------------------------------------------------------
         */
        double scheduledArrivalMinutes = 0.0;

        if (targetRouteStation.getArrivalTime() != null) {

            scheduledArrivalMinutes =
                    targetRouteStation
                            .getArrivalTime()
                            .toSecondOfDay()
                            / 60.0;
        }

        /*
         * --------------------------------------------------------
         * Current delay
         * --------------------------------------------------------
         *
         * This remains the current observed delay.
         */
        double currentDelayMinutes =
                delayCalculationService
                        .calculateDelayMinutes(trainNo);

        /*
         * --------------------------------------------------------
         * Historical average delay
         * --------------------------------------------------------
         *
         * This is target-station specific.
         */
        double historicalAverageDelay =
                historicalDelayService
                        .getHistoricalAverageDelay(
                                trainNo,
                                targetStation,
                                state.getLastUpdated()
                        );

        /*
         * --------------------------------------------------------
         * Time features
         * --------------------------------------------------------
         */
        ZonedDateTime zonedTime =
                state.getLastUpdated()
                        .atZone(
                                ZoneId.systemDefault()
                        );

        int timeOfDay =
                zonedTime.getHour();

        int dayOfWeek =
                zonedTime.getDayOfWeek()
                        .getValue();

        /*
         * --------------------------------------------------------
         * Delay type
         * --------------------------------------------------------
         *
         * We use the currently observed delay type.
         *
         * Future delay type is not known yet.
         */
        String delayType =
                state.getDelayType() == null
                        ? "NONE"
                        : state.getDelayType().name();

        /*
         * --------------------------------------------------------
         * Build ML request
         * --------------------------------------------------------
         */
        MlPredictionRequest request =
                new MlPredictionRequest(

                        /*
                         * Current speed is used as the projected
                         * operating speed.
                         */
                        state.getSpeedKmh(),

                        /*
                         * NEW:
                         * Distance specifically to this target.
                         */
                        distanceToTargetKm,

                        /*
                         * NEW:
                         * Distance remaining after this target.
                         */
                        distanceToDestinationKm,

                        currentDelayMinutes,

                        historicalAverageDelay,

                        scheduledArrivalMinutes,

                        timeOfDay,

                        dayOfWeek,

                        targetStation,

                        delayType
                );

        /*
         * --------------------------------------------------------
         * Debug logging
         * --------------------------------------------------------
         */
        System.out.println(
                "===== ML PREDICTION REQUEST ====="
        );

        System.out.println(request);

        System.out.println(
                "================================="
        );

        /*
         * --------------------------------------------------------
         * Call ML API
         * --------------------------------------------------------
         */
        MlPredictionResponse response =
                restClient.post()
                        .uri(predictionUrl)
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(request)
                        .retrieve()
                        .body(
                                MlPredictionResponse.class
                        );

        /*
         * --------------------------------------------------------
         * Validate response
         * --------------------------------------------------------
         */
        if (response == null) {

            throw new RuntimeException(
                    "ML API returned an empty response"
            );
        }

        /*
         * --------------------------------------------------------
         * Return predicted delay
         * --------------------------------------------------------
         */
        return response.predictedDelayMinutes();
    }
}