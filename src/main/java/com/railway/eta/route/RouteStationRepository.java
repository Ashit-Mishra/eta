package com.railway.eta.route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteStationRepository
        extends JpaRepository<RouteStation, Long> {

    List<RouteStation> findByRouteRouteCodeOrderBySequenceNumber(
            String routeCode
    );
    @Query("""
        SELECT rs
        FROM RouteStation rs
        JOIN FETCH rs.station
        WHERE rs.route.id = :routeId
        ORDER BY rs.sequenceNumber ASC
        """)
    List<RouteStation> findByRouteIdOrderBySequenceNumberAsc(
            @Param("routeId") Long routeId
    );
}