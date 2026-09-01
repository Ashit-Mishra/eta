package com.railway.eta.history;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "train_runs")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_no", nullable = false)
    private String trainNo;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "final_delay_minutes")
    private Double finalDelayMinutes;

    @Column(nullable = false)
    private String status;
}