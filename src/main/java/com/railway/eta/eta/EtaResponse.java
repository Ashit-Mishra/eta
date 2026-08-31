package com.railway.eta.eta;

public record EtaResponse(
        String trainNo,
        String currentStation,
        String nextStation,
        double distanceToNextStationKm,
        double distanceToDestinationKm,
        double speedKmh,
        double etaToNextStationMinutes,
        double etaToDestinationMinutes,
        double delayMinutes
) {
}
