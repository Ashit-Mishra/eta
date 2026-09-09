package com.railway.eta.route.dto;

import java.time.LocalTime;

public record RouteStationResponse(
        Integer sequenceNumber,
        String stationCode,
        String stationName,
        LocalTime arrivalTime,
        LocalTime departureTime,
        Integer day
) {
}
