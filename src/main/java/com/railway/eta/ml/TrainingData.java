package com.railway.eta.ml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainingData {

    // Which simulation/run produced this training example
    private Long runId;

    // Train
    private String trainNo;

    // Station for which we are predicting arrival delay
    private String stationCode;

    // When the training observation was made
    private Instant timestamp;

    // -----------------------------
    // Current train conditions
    // -----------------------------

    private double speedKmh;

    private double distanceToStationKm;

    private double distanceToDestinationKm;

    // Delay currently observed at this point
    private double currentDelayMinutes;

    // Average delay from previous runs
    private double historicalAverageDelayMinutes;

    // -----------------------------
    // Schedule / time features
    // -----------------------------

    // Scheduled arrival converted to minutes from midnight
    private double scheduledArrivalMinutes;

    // Hour of day: 0 - 23
    private int timeOfDay;

    // Java DayOfWeek value: 1 - 7
    private int dayOfWeek;

    // -----------------------------
    // ML TARGET
    // -----------------------------

    // Actual delay when the train reached this station
    private double actualDelayAtStationMinutes;
}