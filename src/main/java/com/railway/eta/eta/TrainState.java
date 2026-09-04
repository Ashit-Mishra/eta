package com.railway.eta.eta;

import com.railway.eta.simulator.TrainSimulationState.DelayType;
import lombok.Data;

import java.time.Instant;

@Data
public class TrainState {

    private String trainNo;

    private double latitude;

    private double longitude;

    private double speedKmh;

    private Instant lastUpdated;

    private String currentStation;

    private String nextStation;

    private double distanceToNextStationKm;

    private String status;

    private double distanceToDestinationKm;

    private DelayType delayType = DelayType.NONE;
}