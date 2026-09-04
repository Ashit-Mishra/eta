package com.railway.eta.train;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByTrainNo(String trainNo);
    @Query("""
    SELECT t
    FROM Train t
    JOIN t.route r
    WHERE t.active = true
      AND r.geometryJson IS NOT NULL
      AND r.geometryJson <> ''
    ORDER BY t.trainNo
""")
    List<Train> findAllSimulatableTrains();
}