package com.railway.eta.history;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "station_arrival_history")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StationArrivalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_no", nullable = false)
    private String trainNo;

    @Column(name = "station_code", nullable = false)
    private String stationCode;

    @Column(name = "scheduled_arrival")
    private Instant scheduledArrival;

    @Column(name = "actual_arrival", nullable = false)
    private Instant actualArrival;

    @Column(name = "delay_minutes", nullable = false)
    private double delayMinutes;

    @Column(name = "run_id", nullable = false)
    private Long runId;
}