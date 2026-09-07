package com.railway.eta.eta;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/eta/stations")
public class RemainingStationEtaController {

    private final RemainingStationEtaService service;

    public RemainingStationEtaController(
            RemainingStationEtaService service
    ) {
        this.service = service;
    }

    @GetMapping("/{trainNo}")
    public List<RemainingStationEtaResponse> getRemainingStationEta(
            @PathVariable String trainNo
    ) {
        return service.calculate(trainNo);
    }
}