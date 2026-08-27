package com.railway.eta.route;

import com.railway.eta.route.dto.RouteStationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/routes")
public class RouteStationController {

    private final RouteStationService routeStationService;

    @GetMapping("/{routeCode}/stations")
    public List<RouteStationResponse> getRouteStations(
            @PathVariable String routeCode) {

        return routeStationService.getRouteStations(routeCode);
    }
}
