package com.railway.eta.ingestion.dto;

import com.railway.eta.simulator.TrainSimulationState.DelayType;

import java.time.Instant;

public record GpsEvent(
        String trainNo,
        Long runId,
        double latitude,
        double longitude,
        double speedKmh,
        Instant timestamp,
        String currentStation,
        String nextStation,
        DelayType delayType
) {
}