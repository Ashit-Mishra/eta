package com.railway.eta.station;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    public List<Station> getAllStations() {
        return stationRepository.findAll();
    }

    public Station getStationByCode(String code) {
        return stationRepository.findByCode(code)
                .orElseThrow(() ->
                        new RuntimeException("Station not found: " + code));
    }
}