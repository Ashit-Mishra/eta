package com.railway.eta.route;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteService {

    private final RouteRepository routeRepository;

    public RouteService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public Route getRouteByCode(String routeCode) {
        return routeRepository.findByRouteCode(routeCode)
                .orElseThrow(() ->
                        new RuntimeException("Route not found: " + routeCode));
    }
}