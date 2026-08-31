package com.railway.eta.eta;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/live")
public class TrainStateController {

    private final TrainStateService trainStateService;

    public TrainStateController(
            TrainStateService trainStateService
    ) {
        this.trainStateService = trainStateService;
    }

    @GetMapping
    public Map<String, TrainState> getAllTrains() {

        return trainStateService.getAll();
    }

    @GetMapping("/{trainNo}")
    public TrainState getTrain(
            @PathVariable String trainNo
    ) {

        return trainStateService.get(trainNo);
    }
}