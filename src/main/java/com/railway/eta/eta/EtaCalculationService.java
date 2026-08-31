package com.railway.eta.eta;

import org.springframework.stereotype.Service;

@Service
public class EtaCalculationService {

    private final TrainStateService trainStateService;

    public EtaCalculationService(
            TrainStateService trainStateService
    ) {
        this.trainStateService = trainStateService;
    }

    public EtaResponse calculateEta(String trainNo) {

        TrainState state =
                trainStateService.get(trainNo);

        if (state == null) {

            throw new RuntimeException(
                    "No live state found for train " + trainNo
            );
        }

        double speedKmh =
                state.getSpeedKmh();

        if (speedKmh <= 0) {

            throw new RuntimeException(
                    "Train is not moving"
            );
        }

        double distanceToNext =
                state.getDistanceToNextStationKm();

        double distanceToDestination =
                state.getDistanceToDestinationKm();

        double etaToNext =
                (distanceToNext / speedKmh) * 60;

        double etaToDestination =
                (distanceToDestination / speedKmh) * 60;

        return new EtaResponse(
                trainNo,
                state.getCurrentStation(),
                state.getNextStation(),
                distanceToNext,
                distanceToDestination,
                speedKmh,
                etaToNext,
                etaToDestination
        );
    }
}