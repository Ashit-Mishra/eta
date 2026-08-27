package com.railway.eta.train;

import com.railway.eta.train.dto.TrainResponse;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainService {

    private final TrainRepository trainRepository;

    @Transactional(readOnly = true)
    public List<TrainResponse> getAllTrains() {
        return trainRepository.findAll()
                .stream()
                .map(train -> new TrainResponse(
                        train.getId(),
                        train.getTrainNo(),
                        train.getName(),
                        train.getRoute().getRouteCode(),
                        train.getActive()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TrainResponse getTrain(String trainNo) {
        Train train = trainRepository.findByTrainNo(trainNo)
                .orElseThrow(() ->
                        new RuntimeException("Train not found: " + trainNo));

        return new TrainResponse(
                train.getId(),
                train.getTrainNo(),
                train.getName(),
                train.getRoute().getRouteCode(),
                train.getActive()
        );
    }
}
