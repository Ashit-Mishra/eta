package com.railway.eta.route;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStationRepository
        extends JpaRepository<RouteStation, Long> {

    List<RouteStation> findByRouteRouteCodeOrderBySequenceNumber(
            String routeCode
    );
}