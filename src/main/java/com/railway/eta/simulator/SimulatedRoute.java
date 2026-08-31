package com.railway.eta.simulator;

import java.util.List;

public record SimulatedRoute(
        String trainNo,
        List<GeoPoint> geometry,
        double totalDistanceKm
) {
}