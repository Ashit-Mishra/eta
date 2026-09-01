package com.railway.eta.history;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TrainRunService {

    private final TrainRunRepository repository;

    public TrainRunService(
            TrainRunRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public TrainRun startRun(
            String trainNo,
            Instant startTime
    ) {

        TrainRun run = new TrainRun();

        run.setTrainNo(trainNo);
        run.setStartTime(startTime);
        run.setStatus("RUNNING");

        return repository.save(run);
    }

    @Transactional
    public void completeRun(
            Long runId,
            Instant endTime,
            double finalDelayMinutes
    ) {

        TrainRun run =
                repository.findById(runId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train run not found: "
                                                + runId
                                )
                        );

        run.setEndTime(endTime);
        run.setFinalDelayMinutes(
                finalDelayMinutes
        );
        run.setStatus("COMPLETED");

        repository.save(run);
    }
}