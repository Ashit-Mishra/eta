package com.railway.eta.eta;

import com.railway.eta.ml.MlPredictionService;
import org.springframework.stereotype.Service;

@Service
public class StationEtaService {

    private final TrainStateService trainStateService;
    private final MlPredictionService mlPredictionService;

    public StationEtaService(
            TrainStateService trainStateService,
            MlPredictionService mlPredictionService
    ) {
        this.trainStateService = trainStateService;
        this.mlPredictionService = mlPredictionService;
    }

    public StationEtaResponse calculateNextStationEta(String trainNo) {

        TrainState state =
                trainStateService.get(trainNo);

        if (state == null) {
            throw new RuntimeException(
                    "No live state found for train " + trainNo
            );
        }

        if (state.getNextStation() == null) {
            throw new RuntimeException(
                    "Train has reached its destination"
            );
        }

        double speedKmh =
                state.getSpeedKmh();

        double distanceToNext =
                state.getDistanceToNextStationKm();

        double baseEta = 0.0;

        if (speedKmh > 0) {
            baseEta =
                    (distanceToNext / speedKmh) * 60;
        }

        double predictedDelay =
                mlPredictionService.predictDelay(
                        trainNo,
                        state
                );

        double predictedEta =
                baseEta + predictedDelay;

        return new StationEtaResponse(
                trainNo,
                state.getNextStation(),
                baseEta,
                predictedDelay,
                predictedEta
        );
    }
}