package com.railway.eta.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GpsHistoryRepository
        extends JpaRepository<GpsHistory, Long> {

    List<GpsHistory> findByTrainNoOrderByTimestampAsc(
            String trainNo
    );

    List<GpsHistory> findByRunIdOrderByTimestampAsc(
            Long runId
    );
}