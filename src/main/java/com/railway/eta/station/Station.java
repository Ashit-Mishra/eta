package com.railway.eta.station;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "stations")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    private Double latitude;

    private Double longitude;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}