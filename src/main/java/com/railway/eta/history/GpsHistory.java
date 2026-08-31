package com.railway.eta.history;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "gps_history")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GpsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_no", nullable = false)
    private String trainNo;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "speed_kmh", nullable = false)
    private double speedKmh;

    @Column(nullable = false)
    private Instant timestamp;
}