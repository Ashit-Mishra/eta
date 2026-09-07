package com.railway.eta.eta;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eta/station")
public class StationEtaController {

    private final StationEtaService stationEtaService;

    public StationEtaController(
            StationEtaService stationEtaService
    ) {
        this.stationEtaService = stationEtaService;
    }

    @GetMapping("/{trainNo}")
    public StationEtaResponse getNextStationEta(
            @PathVariable String trainNo
    ) {
        return stationEtaService.calculateNextStationEta(trainNo);
    }
}