package com.railway.eta.history;

import org.springframework.stereotype.Service;

import java.time.Instant;

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
        /*
         * Historical delay must not be restricted to the current day.
         * The simulator creates runs on the current day, so the old
         * current-day filter caused the historical average to be 0.0.
         *
         * Use all recorded arrivals before the current observation.
         * Negative averages are clamped because the UI metric is
         * specifically "minutes late".
         */
        double average = repository
                .findHistoricalAverageDelay(
                        stationCode,
                        currentTime
                )
                .orElse(0.0);

        return Math.max(0.0, average);
    }
}
