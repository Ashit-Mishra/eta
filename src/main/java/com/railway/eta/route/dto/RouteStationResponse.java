package com.railway.eta.route.dto;

public record RouteStationResponse(
        Integer sequenceNumber,
        String stationCode,
        String stationName
) {
}