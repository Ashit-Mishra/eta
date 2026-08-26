package com.railway.eta.train;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrainService {

    private final TrainRepository trainRepository;

    public TrainService(TrainRepository trainRepository) {
        this.trainRepository = trainRepository;
    }

    public List<Train> getAllTrains() {
        return trainRepository.findAll();
    }

    public Train getTrain(String trainNo) {
        return trainRepository.findByTrainNo(trainNo)
                .orElseThrow(() ->
                        new RuntimeException("Train not found: " + trainNo));
    }
}
