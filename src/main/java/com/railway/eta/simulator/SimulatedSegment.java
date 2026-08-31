package com.railway.eta.simulator;

import java.util.List;

public record SimulatedSegment(
        SimulatedStation from,
        SimulatedStation to,
        double distanceKm,
        List<SimulatedPoint> geometryPoints
) {
}