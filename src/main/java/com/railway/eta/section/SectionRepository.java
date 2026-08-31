package com.railway.eta.section;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {

    Optional<Section> findBySectionCode(String sectionCode);
    Optional<Section> findByFromStationIdAndToStationId(
            Long fromStationId,
            Long toStationId
    );
    List<Section> findByFromStationIdIn(List<Long> stationIds);
}