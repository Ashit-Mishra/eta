package com.railway.eta.simulator;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimulatedTrainState {

    private String trainNo;

    private int currentSegment;

    private double progress;

    private double speedKmh;
}