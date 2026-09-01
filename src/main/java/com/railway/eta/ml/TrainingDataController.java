package com.railway.eta.ml;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ml/training-data")
public class TrainingDataController {

    private final TrainingDataService trainingDataService;

    public TrainingDataController(
            TrainingDataService trainingDataService
    ) {
        this.trainingDataService =
                trainingDataService;
    }

    @GetMapping("/{runId}")
    public List<TrainingData> getTrainingData(
            @PathVariable Long runId
    ) {

        return trainingDataService.generateForRun(
                runId
        );
    }

    @GetMapping
    public List<TrainingData> getAllTrainingData() {
        return trainingDataService
                .generateForAllCompletedRuns();
    }
}