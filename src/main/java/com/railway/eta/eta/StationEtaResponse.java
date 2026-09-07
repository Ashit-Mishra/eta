package com.railway.eta.eta;

public record StationEtaResponse(
        String trainNo,
        String stationCode,
        double baseEtaMinutes,
        double predictedDelayMinutes,
        double predictedEtaMinutes
) {}