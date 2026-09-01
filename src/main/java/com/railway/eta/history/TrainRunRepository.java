package com.railway.eta.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainRunRepository
        extends JpaRepository<TrainRun, Long> {

    Optional<TrainRun> findFirstByTrainNoAndStatusOrderByStartTimeDesc(
            String trainNo,
            String status
    );

    List<TrainRun> findByTrainNoAndStatusOrderByStartTimeAsc(
            String trainNo,
            String status
    );

    List<TrainRun> findByStatusOrderByStartTimeAsc(
            String status
    );
}