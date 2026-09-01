package com.railway.eta.history;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class HistoricalDelayService {

    private final StationArrivalHistoryRepository repository;

    public HistoricalDelayService(
            StationArrivalHistoryRepository repository
    ) {
        this.repository = repository;
    }

    public double getHistoricalAverageDelay(
            String trainNo,
            String stationCode,
            Instant currentTime
    ) {

        LocalDate currentDate =
                currentTime
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

        Instant startOfCurrentDay =
                currentDate
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant();

        return repository
                .findHistoricalAverageDelay(
                        trainNo,
                        stationCode,
                        startOfCurrentDay
                )
                .orElse(0.0);
    }
}