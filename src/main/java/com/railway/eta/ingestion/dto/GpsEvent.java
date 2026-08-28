package com.railway.eta.ingestion.dto;

import java.time.Instant;

public record GpsEvent(
        String trainNo,
        double latitude,
        double longitude,
        double speedKmh,
        Instant timestamp
) {
}