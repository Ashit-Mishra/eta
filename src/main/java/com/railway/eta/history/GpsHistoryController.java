package com.railway.eta.history;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class GpsHistoryController {

    private final GpsHistoryRepository gpsHistoryRepository;

    public GpsHistoryController(
            GpsHistoryRepository gpsHistoryRepository
    ) {
        this.gpsHistoryRepository = gpsHistoryRepository;
    }

    @GetMapping("/{trainNo}")
    public List<GpsHistory> getHistory(
            @PathVariable String trainNo
    ) {

        return gpsHistoryRepository
                .findByTrainNoOrderByTimestampAsc(trainNo);
    }
}