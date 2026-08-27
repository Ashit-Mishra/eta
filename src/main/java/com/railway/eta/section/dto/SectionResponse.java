package com.railway.eta.section.dto;

public record SectionResponse(
        Long id,
        String sectionCode,
        String fromStationCode,
        String toStationCode,
        Double distanceKm
) {
}