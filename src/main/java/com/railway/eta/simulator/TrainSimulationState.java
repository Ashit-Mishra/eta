package com.railway.eta.simulator;

import java.time.Instant;
import java.util.List;


public class TrainSimulationState {

    // ========================================================
    // TRAIN
    // ========================================================

    private final String trainNo;

    private final List<SimulatedSegment> segments;

    // ========================================================
    // ROUTE PROGRESS
    // ========================================================

    private int currentSegment = 0;

    private double distanceTravelledKm = 0.0;

    // ========================================================
    // SPEED
    // ========================================================

    private double speedKmh = 80.0;

    // ========================================================
    // SIMULATION CLOCK
    // ========================================================

    private Instant simulationTime;

    private Instant simulationStartTime;

    private long lastRealTime;

    // ========================================================
    // DELAY
    // ========================================================

    public enum DelayType {
        NONE,
        SPEED,
        SIGNAL,
        WEATHER
    }

    private DelayType currentDelayType = DelayType.NONE;

    // ========================================================
    // TRAIN RUN
    // ========================================================

    private Long runId;

    private boolean completed = false;

    // ========================================================
    // CONSTRUCTOR
    // ========================================================

    public TrainSimulationState(
            String trainNo,
            List<SimulatedSegment> segments,
            Instant simulationTime,
            Long runId
    ) {
        this.trainNo = trainNo;
        this.segments = segments;
        this.simulationTime = simulationTime;
        this.simulationStartTime = simulationTime;
        this.runId = runId;
        this.lastRealTime = System.currentTimeMillis();
    }

    // ========================================================
    // GETTERS
    // ========================================================

    public String getTrainNo() {
        return trainNo;
    }

    public List<SimulatedSegment> getSegments() {
        return segments;
    }

    public int getCurrentSegment() {
        return currentSegment;
    }

    public double getDistanceTravelledKm() {
        return distanceTravelledKm;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public Instant getSimulationTime() {
        return simulationTime;
    }

    public Instant getSimulationStartTime() {
        return simulationStartTime;
    }

    public long getLastRealTime() {
        return lastRealTime;
    }

    public DelayType getCurrentDelayType() {
        return currentDelayType;
    }

    public Long getRunId() {
        return runId;
    }

    public boolean isCompleted() {
        return completed;
    }

    // ========================================================
    // SETTERS
    // ========================================================

    public void setCurrentSegment(int currentSegment) {
        this.currentSegment = currentSegment;
    }

    public void setDistanceTravelledKm(double distanceTravelledKm) {
        this.distanceTravelledKm = distanceTravelledKm;
    }

    public void setSpeedKmh(double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public void setSimulationTime(Instant simulationTime) {
        this.simulationTime = simulationTime;
    }

    public void setLastRealTime(long lastRealTime) {
        this.lastRealTime = lastRealTime;
    }

    public void setCurrentDelayType(DelayType currentDelayType) {
        this.currentDelayType = currentDelayType;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}