package com.railway.eta.route;

import com.railway.eta.route.dto.RouteStationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteStationService {

    private final RouteStationRepository repository;

    @Transactional(readOnly = true)
    public List<RouteStationResponse> getRouteStations(
            String routeCode) {

        return repository
                .findByRouteRouteCodeOrderBySequenceNumber(routeCode)
                .stream()
                .map(routeStation -> new RouteStationResponse(
                        routeStation.getSequenceNumber(),
                        routeStation.getStation().getCode(),
                        routeStation.getStation().getName()
                ))
                .toList();
    }
}
