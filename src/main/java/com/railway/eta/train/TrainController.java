package com.railway.eta.train;

import com.railway.eta.train.dto.TrainResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trains")
@RequiredArgsConstructor
public class TrainController {

    private final TrainService trainService;

    @GetMapping
    public List<TrainResponse> getAllTrains() {
        return trainService.getAllTrains();
    }

    @GetMapping("/{trainNo}")
    public TrainResponse getTrain(@PathVariable String trainNo) {
        return trainService.getTrain(trainNo);
    }
}