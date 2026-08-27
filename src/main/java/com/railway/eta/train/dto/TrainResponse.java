package com.railway.eta.train.dto;

public record TrainResponse(
        Long id,
        String trainNo,
        String name,
        String routeCode,
        boolean active
) {
}