package com.railway.eta.eta;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eta")
public class EtaController {

    private final EtaCalculationService etaCalculationService;

    public EtaController(
            EtaCalculationService etaCalculationService
    ) {
        this.etaCalculationService = etaCalculationService;
    }

    @GetMapping("/{trainNo}")
    public EtaResponse getEta(
            @PathVariable String trainNo
    ) {

        return etaCalculationService.calculateEta(trainNo);
    }
}