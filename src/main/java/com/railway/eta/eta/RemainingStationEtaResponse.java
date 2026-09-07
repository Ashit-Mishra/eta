package com.railway.eta.eta;

public record RemainingStationEtaResponse(
        String stationCode,
        double baseEtaMinutes,
        double predictedDelayMinutes,
        double predictedEtaMinutes
) {}