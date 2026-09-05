package com.railway.eta.ml;

import com.railway.eta.history.GpsHistory;
import com.railway.eta.history.GpsHistoryRepository;
import com.railway.eta.history.HistoricalDelayService;
import com.railway.eta.history.StationArrivalHistory;
import com.railway.eta.history.StationArrivalHistoryRepository;
import com.railway.eta.history.TrainRun;
import com.railway.eta.history.TrainRunRepository;
import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.simulator.TrainSimulationState;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.railway.eta.simulator.TrainSimulationState.DelayType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrainingDataService {

    private final TrainRunRepository trainRunRepository;
    private final GpsHistoryRepository gpsHistoryRepository;
    private final StationArrivalHistoryRepository stationArrivalHistoryRepository;
    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final HistoricalDelayService historicalDelayService;

    public TrainingDataService(
            TrainRunRepository trainRunRepository,
            GpsHistoryRepository gpsHistoryRepository,
            StationArrivalHistoryRepository stationArrivalHistoryRepository,
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository,
            HistoricalDelayService historicalDelayService
    ) {
        this.trainRunRepository = trainRunRepository;
        this.gpsHistoryRepository = gpsHistoryRepository;
        this.stationArrivalHistoryRepository =
                stationArrivalHistoryRepository;
        this.trainRepository = trainRepository;
        this.routeStationRepository =
                routeStationRepository;
        this.historicalDelayService =
                historicalDelayService;
    }

    /**
     * Generate station-level training data for one
     * completed train run.
     */
    @Transactional(readOnly = true)
    public List<TrainingData> generateForRun(Long runId) {

        // ----------------------------------------------------
        // 1. Find completed run
        // ----------------------------------------------------

        TrainRun run =
                trainRunRepository.findById(runId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train run not found: "
                                                + runId
                                )
                        );

        if (!"COMPLETED".equals(run.getStatus())) {
            throw new RuntimeException(
                    "Training data can only be generated "
                            + "for completed runs"
            );
        }

        String trainNo =
                run.getTrainNo();

        // ----------------------------------------------------
        // 2. Get train
        // ----------------------------------------------------

        Train train =
                trainRepository
                        .findByTrainNo(trainNo)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: "
                                                + trainNo
                                )
                        );

        // ----------------------------------------------------
        // 3. Get ordered route stations
        // ----------------------------------------------------

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        if (routeStations.isEmpty()) {
            throw new RuntimeException(
                    "No route stations found for train "
                            + trainNo
            );
        }

        // ----------------------------------------------------
        // 4. Get GPS history for this run
        // ----------------------------------------------------

        List<GpsHistory> gpsHistory =
                gpsHistoryRepository
                        .findByRunIdOrderByTimestampAsc(
                                runId
                        );

        if (gpsHistory.isEmpty()) {
            throw new RuntimeException(
                    "No GPS history found for run "
                            + runId
            );
        }

        // ----------------------------------------------------
        // 5. Get actual station arrivals for this run
        // ----------------------------------------------------

        List<StationArrivalHistory> arrivals =
                stationArrivalHistoryRepository
                        .findByRunIdOrderByActualArrivalAsc(
                                runId
                        );

        if (arrivals.isEmpty()) {
            throw new RuntimeException(
                    "No station arrival history found "
                            + "for run " + runId
            );
        }

        // ----------------------------------------------------
        // 6. Create one training row per station arrival
        // ----------------------------------------------------

        List<TrainingData> trainingData =
                new ArrayList<>();

        for (StationArrivalHistory arrival : arrivals) {

            String stationCode =
                    arrival.getStationCode();

            RouteStation routeStation =
                    findRouteStation(
                            stationCode,
                            routeStations
                    );

            if (routeStation == null) {
                continue;
            }

            GpsHistory gps =
                    findGpsBeforeArrival(
                            gpsHistory,
                            arrival.getActualArrival()
                    );

            if (gps == null) {
                continue;
            }

            // ------------------------------------------------
            // Current delay at the observation point
            // ------------------------------------------------

            double currentDelayMinutes =
                    calculateCurrentDelay(
                            gps,
                            routeStation,
                            routeStations
                    );

            // ------------------------------------------------
            // Historical average delay
            // ------------------------------------------------

            double historicalAverageDelayMinutes =
                    historicalDelayService
                            .getHistoricalAverageDelay(
                                    trainNo,
                                    stationCode,
                                    arrival.getActualArrival()
                            );

            // ------------------------------------------------
            // Distance to station
            // ------------------------------------------------

            double distanceToStationKm =
                    calculateDistance(
                            gps.getLatitude(),
                            gps.getLongitude(),
                            routeStation
                                    .getStation()
                                    .getLatitude(),
                            routeStation
                                    .getStation()
                                    .getLongitude()
                    );

            // ------------------------------------------------
            // Distance to destination
            // ------------------------------------------------

            double distanceToDestinationKm =
                    calculateDistanceToDestination(
                            gps,
                            routeStation,
                            routeStations
                    );

            // ------------------------------------------------
            // Schedule features
            // ------------------------------------------------

            double scheduledArrivalMinutes =
                    getScheduledArrivalMinutes(
                            routeStation
                    );

            int timeOfDay =
                    getTimeOfDay(
                            arrival.getActualArrival()
                    );

            int dayOfWeek =
                    arrival.getActualArrival()
                            .atZone(
                                    ZoneId.of(
                                            "Asia/Kolkata"
                                    )
                            )
                            .getDayOfWeek()
                            .getValue();

            // ------------------------------------------------
            // Create training row
            // ------------------------------------------------

            TrainingData data =
                    new TrainingData();

            data.setRunId(runId);

            data.setTrainNo(trainNo);

            data.setStationCode(stationCode);

            data.setTimestamp(
                    gps.getTimestamp()
            );

            data.setSpeedKmh(
                    gps.getSpeedKmh()
            );

            data.setDistanceToStationKm(
                    distanceToStationKm
            );

            data.setDistanceToDestinationKm(
                    distanceToDestinationKm
            );

            data.setCurrentDelayMinutes(
                    currentDelayMinutes
            );

            data.setHistoricalAverageDelayMinutes(
                    historicalAverageDelayMinutes
            );

            data.setScheduledArrivalMinutes(
                    scheduledArrivalMinutes
            );

            data.setTimeOfDay(
                    timeOfDay
            );

            data.setDayOfWeek(
                    dayOfWeek
            );
            data.setDelayType(
                    findDelayTypeBetweenStations(
                            gpsHistory,
                            arrival.getActualArrival(),
                            findPreviousArrivalTime(arrivals, arrival)
                    )
            );

            // ------------------------------------------------
            // TARGET
            //
            // This is the actual delay when the train
            // reached this station.
            // ------------------------------------------------

            data.setActualDelayAtStationMinutes(
                    arrival.getDelayMinutes()
            );

            trainingData.add(data);
        }

        return trainingData;
    }

    // ========================================================
    // Find route station
    // ========================================================

    @Transactional(readOnly = true)
    public List<TrainingData> generateForAllCompletedRuns() {

        List<TrainRun> completedRuns =
                trainRunRepository
                        .findByStatusOrderByStartTimeAsc("COMPLETED");

        List<TrainingData> allTrainingData =
                new ArrayList<>();

        for (TrainRun run : completedRuns) {

            try {
                List<TrainingData> runData =
                        generateForRun(run.getId());

                allTrainingData.addAll(runData);

            } catch (RuntimeException e) {
                System.out.println(
                        "Skipping run " + run.getId()
                                + ": " + e.getMessage()
                );
            }
        }

        return allTrainingData;
    }

    private RouteStation findRouteStation(
            String stationCode,
            List<RouteStation> routeStations
    ) {

        for (RouteStation routeStation : routeStations) {

            if (routeStation
                    .getStation()
                    .getCode()
                    .equals(stationCode)) {

                return routeStation;
            }
        }

        return null;
    }

    // ========================================================
    // Find latest GPS observation before station arrival
    // ========================================================

    private GpsHistory findGpsBeforeArrival(
            List<GpsHistory> gpsHistory,
            Instant arrivalTime
    ) {

        GpsHistory latest = null;

        for (GpsHistory gps : gpsHistory) {

            if (gps.getTimestamp()
                    .isAfter(arrivalTime)) {

                break;
            }

            latest = gps;
        }

        return latest;
    }

    // ========================================================
// Find previous station arrival time
// ========================================================

    private Instant findPreviousArrivalTime(
            List<StationArrivalHistory> arrivals,
            StationArrivalHistory currentArrival
    ) {

        Instant previousArrivalTime = null;

        for (StationArrivalHistory arrival : arrivals) {

            if (arrival == currentArrival) {
                break;
            }

            previousArrivalTime = arrival.getActualArrival();
        }

        return previousArrivalTime;
    }


// ========================================================
// Find delay type that occurred between stations
// ========================================================

    private DelayType findDelayTypeBetweenStations(
            List<GpsHistory> gpsHistory,
            Instant currentArrivalTime,
            Instant previousArrivalTime
    ) {

        DelayType detectedDelay = DelayType.NONE;

        for (GpsHistory gps : gpsHistory) {

            Instant timestamp = gps.getTimestamp();

            // Ignore GPS points after current station arrival
            if (timestamp.isAfter(currentArrivalTime)) {
                break;
            }

            // Ignore GPS points before previous station arrival
            if (previousArrivalTime != null
                    && timestamp.isBefore(previousArrivalTime)) {
                continue;
            }

            TrainSimulationState.DelayType delayType = gps.getDelayType();

            // Old GPS records may have NULL delayType.
            if (delayType == null) {
                continue;
            }

            // Priority:
            // WEATHER > SIGNAL > SPEED > NONE

            if (delayType == DelayType.WEATHER) {
                detectedDelay = DelayType.WEATHER;
            }
            else if (delayType == TrainSimulationState.DelayType.SIGNAL
                    && detectedDelay != TrainSimulationState.DelayType.WEATHER) {

                detectedDelay = TrainSimulationState.DelayType.SIGNAL;
            }
            else if (delayType == TrainSimulationState.DelayType.SPEED
                    && detectedDelay == TrainSimulationState.DelayType.NONE) {

                detectedDelay = TrainSimulationState.DelayType.SPEED;
            }
        }

        return detectedDelay;
    }

    // ========================================================
    // Calculate current delay
    // ========================================================

    private double calculateCurrentDelay(
            GpsHistory gps,
            RouteStation targetStation,
            List<RouteStation> routeStations
    ) {

        LocalTime scheduledArrival =
                targetStation.getArrivalTime();

        if (scheduledArrival == null) {
            return 0.0;
        }

        if (gps.getSpeedKmh() <= 0) {
            return 0.0;
        }

        double distanceKm =
                calculateDistance(
                        gps.getLatitude(),
                        gps.getLongitude(),
                        targetStation
                                .getStation()
                                .getLatitude(),
                        targetStation
                                .getStation()
                                .getLongitude()
                );

        double hours =
                distanceKm / gps.getSpeedKmh();

        long travelSeconds =
                (long) (hours * 3600);

        Instant predictedArrival =
                gps.getTimestamp()
                        .plusSeconds(
                                travelSeconds
                        );

        Instant scheduledInstant =
                createScheduledArrival(
                        gps.getTimestamp(),
                        targetStation
                );

        long delaySeconds =
                Duration.between(
                        scheduledInstant,
                        predictedArrival
                ).getSeconds();

        return delaySeconds / 60.0;
    }

    // ========================================================
    // Distance to destination
    // ========================================================

    private double calculateDistanceToDestination(
            GpsHistory gps,
            RouteStation currentStation,
            List<RouteStation> routeStations
    ) {

        int currentIndex = -1;

        for (int i = 0; i < routeStations.size(); i++) {

            if (routeStations
                    .get(i)
                    .getStation()
                    .getCode()
                    .equals(
                            currentStation
                                    .getStation()
                                    .getCode()
                    )) {

                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return 0.0;
        }

        double totalDistance = 0.0;

        // GPS → current/target station
        totalDistance +=
                calculateDistance(
                        gps.getLatitude(),
                        gps.getLongitude(),
                        currentStation
                                .getStation()
                                .getLatitude(),
                        currentStation
                                .getStation()
                                .getLongitude()
                );

        // Current station → destination
        for (
                int i = currentIndex;
                i < routeStations.size() - 1;
                i++
        ) {

            RouteStation from =
                    routeStations.get(i);

            RouteStation to =
                    routeStations.get(i + 1);

            totalDistance +=
                    calculateDistance(
                            from.getStation().getLatitude(),
                            from.getStation().getLongitude(),
                            to.getStation().getLatitude(),
                            to.getStation().getLongitude()
                    );
        }

        return totalDistance;
    }

    // ========================================================
    // Scheduled arrival → minutes from midnight
    // ========================================================

    private double getScheduledArrivalMinutes(
            RouteStation routeStation
    ) {

        if (routeStation.getArrivalTime() == null) {
            return 0.0;
        }

        return routeStation
                .getArrivalTime()
                .toSecondOfDay()
                / 60.0;
    }

    // ========================================================
    // Time of day
    // ========================================================

    private int getTimeOfDay(
            Instant timestamp
    ) {

        return timestamp
                .atZone(
                        ZoneId.of("Asia/Kolkata")
                )
                .getHour();
    }

    // ========================================================
    // Scheduled arrival Instant
    // ========================================================

    private Instant createScheduledArrival(
            Instant simulationTimestamp,
            RouteStation routeStation
    ) {

        if (routeStation.getArrivalTime() == null) {
            return simulationTimestamp;
        }

        var simulationDate =
                simulationTimestamp
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();

        int scheduleDay =
                routeStation.getDay() == null
                        ? 1
                        : routeStation.getDay();

        var scheduledDate =
                simulationDate.plusDays(
                        scheduleDay - 1L
                );

        return routeStation
                .getArrivalTime()
                .atDate(scheduledDate)
                .atZone(
                        ZoneId.of("Asia/Kolkata")
                )
                .toInstant();
    }

    // ========================================================
    // Haversine distance
    // ========================================================

    private double calculateDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        final double EARTH_RADIUS_KM =
                6371.0;

        double lat1 =
                Math.toRadians(latitude1);

        double lat2 =
                Math.toRadians(latitude2);

        double deltaLat =
                Math.toRadians(
                        latitude2 - latitude1
                );

        double deltaLon =
                Math.toRadians(
                        longitude2 - longitude1
                );

        double a =
                Math.sin(deltaLat / 2)
                        * Math.sin(deltaLat / 2)
                        +
                        Math.cos(lat1)
                                * Math.cos(lat2)
                                * Math.sin(deltaLon / 2)
                                * Math.sin(deltaLon / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM * c;
    }
}