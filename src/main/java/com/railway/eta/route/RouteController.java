package com.railway.eta.route;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    @GetMapping
    public List<Route> getAllRoutes() {
        return routeService.getAllRoutes();
    }

    @GetMapping("/{routeCode}")
    public Route getRoute(@PathVariable String routeCode) {
        return routeService.getRouteByCode(routeCode);
    }
}
