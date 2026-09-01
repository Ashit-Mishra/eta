package com.railway.eta.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StationArrivalHistoryRepository
        extends JpaRepository<StationArrivalHistory, Long> {

    List<StationArrivalHistory>
    findByTrainNoAndStationCodeOrderByActualArrivalAsc(
            String trainNo,
            String stationCode
    );

    boolean existsByRunIdAndStationCode(
            Long runId,
            String stationCode
    );

    List<StationArrivalHistory>
    findByRunIdOrderByActualArrivalAsc(Long runId);

    @Query("""
            SELECT AVG(h.delayMinutes)
            FROM StationArrivalHistory h
            WHERE h.trainNo = :trainNo
              AND h.stationCode = :stationCode
              AND h.actualArrival < :before
            """)
    Optional<Double> findHistoricalAverageDelay(
            @Param("trainNo") String trainNo,
            @Param("stationCode") String stationCode,
            @Param("before") Instant before
    );
}