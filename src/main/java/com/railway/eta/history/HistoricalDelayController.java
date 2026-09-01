package com.railway.eta.history;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/historical-delay")
public class HistoricalDelayController {

    private final HistoricalDelayService historicalDelayService;

    public HistoricalDelayController(
            HistoricalDelayService historicalDelayService
    ) {
        this.historicalDelayService =
                historicalDelayService;
    }

    @GetMapping("/{trainNo}/{stationCode}")
    public Map<String, Object> getHistoricalDelay(
            @PathVariable String trainNo,
            @PathVariable String stationCode
    ) {

        double averageDelay =
                historicalDelayService
                        .getHistoricalAverageDelay(
                                trainNo,
                                stationCode,
                                Instant.now()
                        );

        return Map.of(
                "trainNo", trainNo,
                "stationCode", stationCode,
                "historicalAverageDelayMinutes",
                averageDelay
        );
    }
}