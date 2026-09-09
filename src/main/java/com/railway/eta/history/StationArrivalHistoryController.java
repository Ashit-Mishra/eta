package com.railway.eta.history;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/arrival-history")
public class StationArrivalHistoryController {

    private final StationArrivalHistoryService service;

    public StationArrivalHistoryController(
            StationArrivalHistoryService service
    ) {
        this.service = service;
    }

    @GetMapping("/{trainNo}")
    public List<StationArrivalHistoryResponse>
    getArrivalHistory(
            @PathVariable String trainNo
    ) {
        return service.getCurrentRunArrivalHistory(trainNo);
    }
}