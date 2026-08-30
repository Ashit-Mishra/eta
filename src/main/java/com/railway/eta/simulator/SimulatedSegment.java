package com.railway.eta.simulator;

public record SimulatedSegment(
        SimulatedStation from,
        SimulatedStation to,
        double distanceKm
) {
}
