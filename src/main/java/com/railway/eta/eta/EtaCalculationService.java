package com.railway.eta.eta;

import org.springframework.stereotype.Service;

@Service
public class EtaCalculationService {

    private final TrainStateService trainStateService;
    private final DelayCalculationService delayCalculationService;

    public EtaCalculationService(
            TrainStateService trainStateService, DelayCalculationService delayCalculationService
    ) {
        this.trainStateService = trainStateService;
        this.delayCalculationService = delayCalculationService;
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

        double delayMinutes =
                delayCalculationService
                        .calculateDelayMinutes(trainNo);

        return new EtaResponse(
                trainNo,
                state.getCurrentStation(),
                state.getNextStation(),
                distanceToNext,
                distanceToDestination,
                speedKmh,
                etaToNext,
                etaToDestination,
                delayMinutes
        );
    }
}