package com.railway.eta.section;

import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.section.dto.SectionResponse;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final DistanceCalculator distanceCalculator;

    @Transactional
    public void createSectionsForTrain(String trainNo) {

        Train train = trainRepository
                .findByTrainNo(trainNo)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Train not found: " + trainNo
                        )
                );

        Long routeId = train.getRoute().getId();

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                routeId
                        );

        if (routeStations.size() < 2) {
            throw new RuntimeException(
                    "Route must contain at least two stations"
            );
        }

        int created = 0;

        for (int i = 0; i < routeStations.size() - 1; i++) {

            RouteStation from =
                    routeStations.get(i);

            RouteStation to =
                    routeStations.get(i + 1);

            var fromStation = from.getStation();
            var toStation = to.getStation();

            if (fromStation.getLatitude() == null
                    || fromStation.getLongitude() == null
                    || toStation.getLatitude() == null
                    || toStation.getLongitude() == null) {

                System.out.println(
                        "Skipping section "
                                + fromStation.getCode()
                                + " → "
                                + toStation.getCode()
                                + " because coordinates are missing."
                );

                continue;
            }

            double distance =
                    distanceCalculator.calculate(
                            fromStation.getLatitude(),
                            fromStation.getLongitude(),
                            toStation.getLatitude(),
                            toStation.getLongitude()
                    );

            Section section = new Section();

            section.setSectionCode(
                    trainNo
                            + "-"
                            + from.getSequenceNumber()
                            + "-"
                            + to.getSequenceNumber()
            );

            section.setFromStation(fromStation);
            section.setToStation(toStation);
            section.setDistanceKm(distance);

            sectionRepository.save(section);

            created++;

            System.out.printf(
                    "%s → %s : %.2f km%n",
                    fromStation.getCode(),
                    toStation.getCode(),
                    distance
            );
        }

        System.out.println(
                "Sections created: " + created
        );
    }
}