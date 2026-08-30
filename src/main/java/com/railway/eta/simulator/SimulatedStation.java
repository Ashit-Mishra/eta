package com.railway.eta.simulator;

import lombok.Data;


public record SimulatedStation(
        String code,
        String name,
        Double latitude,
        Double longitude,
        Integer sequenceNumber
) {
}