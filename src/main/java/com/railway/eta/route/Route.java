package com.railway.eta.route;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "routes")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_code", nullable = false, unique = true, length = 50)
    private String routeCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "geometry_json", columnDefinition = "TEXT")
    private String geometryJson;

}
